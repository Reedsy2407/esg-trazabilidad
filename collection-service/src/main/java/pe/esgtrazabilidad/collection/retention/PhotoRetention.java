package pe.esgtrazabilidad.collection.retention;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import pe.esgtrazabilidad.collection.evidence.EvidenceStorage;

/**
 * Deletes the photos of check-ins older than the retention period (spec B2, CA-40). The record, its hash and
 * its totals are never touched: only the object in the bucket goes, and the check-in is marked
 * ({@code photo_deleted_at}) so the photo is not looked for again.
 *
 * <p>Order matters (A.5a): the call to the bucket happens OUTSIDE any database transaction and holds no lock;
 * only afterwards is the check-in marked, with a conditional UPDATE ({@code where photo_deleted_at is null}).
 * Zero rows updated means another runner marked it first. Two runs at the same time may both ask the bucket to
 * delete the same object (deleting what is already gone is harmless), and exactly one of them counts it. If the
 * bucket fails, the row is not marked, so the next run tries again.
 *
 * <p>Age is counted from {@code received_at}, the server's clock, not the phone's.
 */
@Component
public class PhotoRetention {

    private static final Logger log = LoggerFactory.getLogger(PhotoRetention.class);

    private final NamedParameterJdbcTemplate jdbc;
    private final EvidenceStorage storage;
    private final int batchSize;

    PhotoRetention(
            NamedParameterJdbcTemplate jdbc, EvidenceStorage storage, @Value("${esg.retention.photo-batch-size:500}") int batchSize) {
        this.jdbc = jdbc;
        this.storage = storage;
        this.batchSize = batchSize;
    }

    /** {@code deleted} counts the check-ins this run marked; {@code failed} the ones the bucket refused. */
    public record PhotoResult(int deleted, int failed) {
    }

    private record Candidate(UUID checkinId, String photoKey) {
    }

    public PhotoResult run(Instant cutoff, Instant now) {
        // No transaction is open here, and none is opened around the bucket calls below.
        List<Candidate> candidates = jdbc.query(
                """
                select id, photo_key from field_checkin
                where photo_key is not null and photo_deleted_at is null and received_at < :cutoff
                order by received_at, id
                limit :limit
                """,
                new MapSqlParameterSource("cutoff", Timestamp.from(cutoff)).addValue("limit", batchSize),
                (rs, i) -> new Candidate(rs.getObject("id", UUID.class), rs.getString("photo_key")));
        int deleted = 0;
        int failed = 0;
        for (Candidate candidate : candidates) {
            try {
                storage.delete(candidate.photoKey());
            } catch (RuntimeException exception) {
                // Not marked: the next run tries again. The key is not logged (it names an association).
                failed++;
                log.warn("event=photo_retention_delete_failed reason={}", exception.getClass().getSimpleName());
                continue;
            }
            int marked = jdbc.update(
                    "update field_checkin set photo_deleted_at = :now where id = :id and photo_deleted_at is null",
                    new MapSqlParameterSource("id", candidate.checkinId()).addValue("now", Timestamp.from(now)));
            deleted += marked;
        }
        log.info("event=photo_retention_run deleted={} failed={}", deleted, failed);
        return new PhotoResult(deleted, failed);
    }

    /** The cut-off for {@code photoMonths} calendar months before {@code now}. */
    static Instant cutoff(Instant now, int photoMonths) {
        return now.atOffset(ZoneOffset.UTC).minusMonths(photoMonths).toInstant();
    }
}
