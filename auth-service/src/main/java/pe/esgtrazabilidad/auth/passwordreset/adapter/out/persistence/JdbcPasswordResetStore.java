package pe.esgtrazabilidad.auth.passwordreset.adapter.out.persistence;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import pe.esgtrazabilidad.auth.passwordreset.port.out.PasswordResetStore;
import pe.esgtrazabilidad.kernel.id.IdGenerator;

/**
 * Plain SQL through JdbcTemplate: the work is a few conditional statements (spend a code only if it is
 * still live, count rows in a window) that read more clearly as SQL than as entities. The columns are
 * timestamptz and are written and read as UTC.
 */
@Component
class JdbcPasswordResetStore implements PasswordResetStore {

    private final JdbcTemplate jdbc;

    JdbcPasswordResetStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    @Override
    @Transactional
    public void purgeOlderThan(Instant cutoff) {
        jdbc.update("DELETE FROM password_reset_request WHERE requested_at < ?", utc(cutoff));
        jdbc.update(
                "DELETE FROM password_reset_code WHERE created_at < ? AND (consumed_at IS NOT NULL OR expires_at < ?)",
                utc(cutoff),
                utc(cutoff));
        jdbc.update(
                "DELETE FROM password_reset_token WHERE created_at < ? AND (consumed_at IS NOT NULL OR expires_at < ?)",
                utc(cutoff),
                utc(cutoff));
    }

    @Override
    public List<Instant> acceptedByIpSince(Kind kind, String ipHmac, Instant since) {
        return jdbc.query(
                "SELECT requested_at FROM password_reset_request"
                        + " WHERE kind = ? AND ip_hmac = ? AND accepted AND requested_at > ? ORDER BY requested_at",
                (rs, row) -> rs.getObject(1, OffsetDateTime.class).toInstant(),
                kind.name(),
                ipHmac,
                utc(since));
    }

    @Override
    public List<Instant> acceptedByEmailSince(Kind kind, String emailHmac, Instant since) {
        return jdbc.query(
                "SELECT requested_at FROM password_reset_request"
                        + " WHERE kind = ? AND email_hmac = ? AND accepted AND requested_at > ? ORDER BY requested_at",
                (rs, row) -> rs.getObject(1, OffsetDateTime.class).toInstant(),
                kind.name(),
                emailHmac,
                utc(since));
    }

    @Override
    public int sentSince(Instant since) {
        Integer count = jdbc.queryForObject(
                "SELECT count(*) FROM password_reset_request WHERE sent AND requested_at > ?", Integer.class, utc(since));
        return count == null ? 0 : count;
    }

    @Override
    public UUID recordRequest(Kind kind, String emailHmac, String ipHmac, Instant at, boolean accepted) {
        UUID id = IdGenerator.generate();
        jdbc.update(
                "INSERT INTO password_reset_request (id, kind, email_hmac, ip_hmac, requested_at, accepted)"
                        + " VALUES (?, ?, ?, ?, ?, ?)",
                id,
                kind.name(),
                emailHmac,
                ipHmac,
                utc(at),
                accepted);
        return id;
    }

    @Override
    public void markSent(UUID requestId) {
        jdbc.update("UPDATE password_reset_request SET sent = true WHERE id = ?", requestId);
    }

    @Override
    @Transactional
    public void replaceCode(UUID codeId, UUID staffUserId, String codeHmac, Instant now, Instant expiresAt) {
        jdbc.update(
                "UPDATE password_reset_code SET consumed_at = ? WHERE staff_user_id = ? AND consumed_at IS NULL",
                utc(now),
                staffUserId);
        jdbc.update(
                "INSERT INTO password_reset_code (id, staff_user_id, code_hmac, created_at, expires_at)"
                        + " VALUES (?, ?, ?, ?, ?)",
                codeId,
                staffUserId,
                codeHmac,
                utc(now),
                utc(expiresAt));
    }

    @Override
    @Transactional
    public void replaceCodeDecoy() {
        Instant now = Instant.now();
        jdbc.update(
                "UPDATE password_reset_code SET consumed_at = ? WHERE staff_user_id = ? AND consumed_at IS NULL",
                utc(now),
                UUID.randomUUID());
        jdbc.update(
                "INSERT INTO password_reset_code (id, staff_user_id, code_hmac, created_at, expires_at)"
                        + " SELECT ?::uuid, ?::uuid, ?, ?::timestamptz, ?::timestamptz WHERE false",
                UUID.randomUUID(),
                UUID.randomUUID(),
                "decoy",
                utc(now),
                utc(now));
    }

    @Override
    @Transactional
    public CheckResult check(UUID staffUserId, Instant now, int maxAttempts, Predicate<LiveCode> matches) {
        List<LiveCode> live = jdbc.query(
                "SELECT id, code_hmac FROM password_reset_code"
                        + " WHERE staff_user_id = ? AND consumed_at IS NULL AND expires_at > ? FOR UPDATE",
                (rs, row) -> new LiveCode(rs.getObject(1, UUID.class), rs.getString(2)),
                staffUserId,
                utc(now));
        if (live.isEmpty()) {
            // Same statements as a mismatch, matching no row: an account with no live code, or no account
            // at all, costs the database what a wrong code costs.
            jdbc.update(
                    "UPDATE password_reset_code SET attempts = attempts + 1,"
                            + " consumed_at = CASE WHEN attempts + 1 >= ? THEN ? ELSE NULL END WHERE id = ?",
                    maxAttempts,
                    utc(now),
                    UUID.randomUUID());
            return new CheckResult(Outcome.NONE, null);
        }
        LiveCode code = live.get(0);
        if (matches.test(code)) {
            return new CheckResult(Outcome.MATCH, code.id());
        }
        jdbc.update(
                "UPDATE password_reset_code SET attempts = attempts + 1,"
                        + " consumed_at = CASE WHEN attempts + 1 >= ? THEN ? ELSE NULL END WHERE id = ?",
                maxAttempts,
                utc(now),
                code.id());
        return new CheckResult(Outcome.MISMATCH, code.id());
    }

    @Override
    public boolean consume(UUID codeId, Instant now) {
        return jdbc.update(
                        "UPDATE password_reset_code SET consumed_at = ?"
                                + " WHERE id = ? AND consumed_at IS NULL AND expires_at > ?",
                        utc(now),
                        codeId,
                        utc(now))
                == 1;
    }

    @Override
    @Transactional
    public void replaceToken(UUID tokenId, UUID staffUserId, String tokenHmac, Instant now, Instant expiresAt) {
        jdbc.update(
                "UPDATE password_reset_token SET consumed_at = ? WHERE staff_user_id = ? AND consumed_at IS NULL",
                utc(now),
                staffUserId);
        jdbc.update(
                "INSERT INTO password_reset_token (id, staff_user_id, token_hmac, created_at, expires_at)"
                        + " VALUES (?, ?, ?, ?, ?)",
                tokenId,
                staffUserId,
                tokenHmac,
                utc(now),
                utc(expiresAt));
    }

    @Override
    public Optional<LiveToken> findLiveToken(UUID tokenId, Instant now) {
        return jdbc.query(
                        "SELECT staff_user_id, token_hmac FROM password_reset_token"
                                + " WHERE id = ? AND consumed_at IS NULL AND expires_at > ?",
                        (rs, row) -> new LiveToken(rs.getObject(1, UUID.class), rs.getString(2)),
                        tokenId,
                        utc(now))
                .stream()
                .findFirst();
    }

    @Override
    public boolean consumeToken(UUID tokenId, Instant now) {
        return jdbc.update(
                        "UPDATE password_reset_token SET consumed_at = ?"
                                + " WHERE id = ? AND consumed_at IS NULL AND expires_at > ?",
                        utc(now),
                        tokenId,
                        utc(now))
                == 1;
    }

    @Override
    public void spendLiveCodes(UUID staffUserId, Instant now) {
        jdbc.update(
                "UPDATE password_reset_code SET consumed_at = ? WHERE staff_user_id = ? AND consumed_at IS NULL",
                utc(now),
                staffUserId);
    }
}
