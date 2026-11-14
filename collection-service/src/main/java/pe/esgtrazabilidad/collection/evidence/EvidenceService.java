package pe.esgtrazabilidad.collection.evidence;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import pe.esgtrazabilidad.collection.exception.CollectionErrors;
import pe.esgtrazabilidad.collection.recycler.ActiveRecyclerGuard;
import pe.esgtrazabilidad.kernel.error.ApplicationException;
import pe.esgtrazabilidad.kernel.security.RecyclerClaims;

/**
 * Photo evidence of a check-in (spec B2, CA-37). The device asks for a signed upload URL, uploads the
 * JPEG straight to the private bucket, and then names the key in its check-in. The server never sees the
 * photo; it records what it agreed to receive (key, size, SHA-256) and later checks the bucket holds it.
 *
 * <p>The key is {@code evidence/{associationId}/{year}/{checkinClientId}.jpg}, built here from the token's
 * association and the check-in's client id, never from what the client says. Nothing here logs a key,
 * a signed URL or a credential.
 */
@Service
public class EvidenceService {

    private static final Logger log = LoggerFactory.getLogger(EvidenceService.class);
    private static final ZoneId LIMA = ZoneId.of("America/Lima");

    private final NamedParameterJdbcTemplate jdbc;
    private final EvidenceStorage storage;
    private final ActiveRecyclerGuard activeRecycler;
    private final Clock clock;
    private final Duration uploadValidity;
    private final Duration readValidity;

    EvidenceService(
            NamedParameterJdbcTemplate jdbc,
            EvidenceStorage storage,
            ActiveRecyclerGuard activeRecycler,
            Clock clock,
            @Value("${esg.evidence.upload-url-minutes}") long uploadUrlMinutes,
            @Value("${esg.evidence.read-url-minutes}") long readUrlMinutes) {
        this.jdbc = jdbc;
        this.storage = storage;
        this.activeRecycler = activeRecycler;
        this.clock = clock;
        this.uploadValidity = Duration.ofMinutes(uploadUrlMinutes);
        this.readValidity = Duration.ofMinutes(readUrlMinutes);
    }

    public record Presigned(String uploadUrl, String key, long expiresIn) {
    }

    private record Agreed(String key, long sizeBytes) {
    }

    /**
     * Records what will be uploaded for this check-in and returns the signed URL. Asking again for the same
     * check-in keeps the same key and refreshes size, hash and expiry.
     *
     * @throws ApplicationException COL-019 when the recycler is not active
     */
    public Presigned presign(RecyclerClaims claims, UUID checkinClientId, long sizeBytes, String sha256Hex) {
        activeRecycler.require(claims.recyclerId());
        // A check-in already accepted keeps its photo: a new URL for the same key would let the device
        // overwrite evidence that was verified when the check-in was registered.
        Integer registered = jdbc.queryForObject(
                "select count(*) from field_checkin where recycler_id = :rid and client_id = :cid",
                new MapSqlParameterSource("rid", claims.recyclerId()).addValue("cid", checkinClientId),
                Integer.class);
        if (registered != null && registered > 0) {
            throw new ApplicationException(CollectionErrors.CLIENT_ID_REUSED);
        }
        Instant now = clock.instant();
        String sha256 = sha256Hex.toLowerCase(Locale.ROOT);
        String proposedKey = "evidence/" + claims.associationId() + "/" + LocalDate.now(clock.withZone(LIMA)).getYear()
                + "/" + checkinClientId + ".jpg";

        // One statement, atomic: a repeat reuses the stored key (the year may have turned since).
        String key;
        try {
            key = upsertAgreement(claims, checkinClientId, proposedKey, sizeBytes, sha256, now);
        } catch (DuplicateKeyException sameKeyOfAnotherRecycler) {
            // The key is unique: another recycler of the association already asked with this client id.
            throw new ApplicationException(CollectionErrors.CLIENT_ID_REUSED);
        }

        String url = storage.presignUpload(key, sizeBytes, sha256, uploadValidity);
        return new Presigned(url, key, uploadValidity.toSeconds());
    }

    private String upsertAgreement(
            RecyclerClaims claims, UUID checkinClientId, String proposedKey, long sizeBytes, String sha256, Instant now) {
        return jdbc.queryForObject(
                """
                insert into evidence_presign (recycler_id, checkin_client_id, association_id, object_key, size_bytes,
                    sha256, presigned_at, expires_at)
                values (:rid, :cid, :aid, :key, :size, :sha, :now, :expires)
                on conflict (recycler_id, checkin_client_id) do update
                set size_bytes = excluded.size_bytes, sha256 = excluded.sha256,
                    presigned_at = excluded.presigned_at, expires_at = excluded.expires_at
                returning object_key
                """,
                new MapSqlParameterSource("rid", claims.recyclerId())
                        .addValue("cid", checkinClientId)
                        .addValue("aid", claims.associationId())
                        .addValue("key", proposedKey)
                        .addValue("size", sizeBytes)
                        .addValue("sha", sha256)
                        .addValue("now", Timestamp.from(now))
                        .addValue("expires", Timestamp.from(now.plus(uploadValidity))),
                String.class);
    }

    /**
     * Checks, BEFORE the check-in's transaction opens, that the photo the check-in names was agreed for this
     * recycler and check-in and that the bucket holds it with the agreed size. A bucket that cannot be reached
     * answers the same as a missing object: COL-018 is retryable.
     *
     * @throws ApplicationException COL-018
     */
    public void verifyForCheckin(UUID recyclerId, UUID checkinClientId, String photoKey) {
        List<Agreed> agreed = jdbc.query(
                "select object_key, size_bytes from evidence_presign where recycler_id = :rid and checkin_client_id = :cid",
                new MapSqlParameterSource("rid", recyclerId).addValue("cid", checkinClientId),
                (rs, i) -> new Agreed(rs.getString("object_key"), rs.getLong("size_bytes")));
        if (agreed.isEmpty() || !agreed.get(0).key().equals(photoKey)) {
            throw new ApplicationException(CollectionErrors.INVALID_EVIDENCE);
        }
        Optional<EvidenceStorage.StoredObject> stored;
        try {
            stored = storage.head(photoKey);
        } catch (RuntimeException exception) {
            log.warn("event=evidence_check_failed reason={}", exception.getClass().getSimpleName());
            throw new ApplicationException(CollectionErrors.INVALID_EVIDENCE);
        }
        if (stored.isEmpty() || stored.get().sizeBytes() != agreed.get(0).sizeBytes()) {
            throw new ApplicationException(CollectionErrors.INVALID_EVIDENCE);
        }
    }

    /** A signed read URL for one photo, valid for a few minutes (staff view, S23). Never a public URL. */
    public String readUrl(String photoKey) {
        return storage.presignRead(photoKey, readValidity);
    }
}
