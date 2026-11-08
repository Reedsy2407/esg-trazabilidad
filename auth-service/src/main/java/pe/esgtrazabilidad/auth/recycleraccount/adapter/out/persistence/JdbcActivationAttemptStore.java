package pe.esgtrazabilidad.auth.recycleraccount.adapter.out.persistence;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.OptionalLong;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import pe.esgtrazabilidad.auth.recycleraccount.port.out.ActivationAttemptStore;
import pe.esgtrazabilidad.kernel.id.IdGenerator;

@Component
class JdbcActivationAttemptStore implements ActivationAttemptStore {

    private static final String IP = "IP";
    private static final String CODE_FAIL = "CODE_FAIL";
    /** Rows older than this are of no use to any window and are deleted as new ones arrive. */
    private static final Duration KEEP = Duration.ofDays(1);

    private final JdbcTemplate jdbc;

    JdbcActivationAttemptStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    @Override
    @Transactional
    public OptionalLong takeAddressSlot(String addressHmac, Instant now, int max, Duration window) {
        // Serialises concurrent attempts of the same address until commit; released automatically.
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtext(?))", Object.class, IP + addressHmac);
        jdbc.update("DELETE FROM recycler_activation_attempt WHERE attempted_at < ?", utc(now.minus(KEEP)));
        OptionalLong wait = waitFor(IP, addressHmac, now, max, window, false);
        if (wait.isEmpty()) {
            insert(IP, addressHmac, now);
        }
        return wait;
    }

    @Override
    public OptionalLong codeBlockedFor(String codeHmac, Instant now, int max, Duration block) {
        return waitFor(CODE_FAIL, codeHmac, now, max, block, true);
    }

    @Override
    public void recordCodeFailure(String codeHmac, Instant now) {
        insert(CODE_FAIL, codeHmac, now);
    }

    /**
     * Address (sliding window): blocked while the {@code max} most recent attempts are all inside the
     * window; it lifts when the oldest of them leaves it.
     *
     * <p>Code ({@code fromNewest}): "blocked for 15 minutes from the last failure". The {@code max} most
     * recent failures trigger a block when they all happened within one window of each other, and the
     * block then lasts a full window from the newest of them, however long ago the first one was.
     */
    private OptionalLong waitFor(String kind, String key, Instant now, int max, Duration window, boolean fromNewest) {
        Instant since = fromNewest ? now.minus(KEEP) : now.minus(window);
        List<Instant> recent = jdbc.query(
                "SELECT attempted_at FROM recycler_activation_attempt"
                        + " WHERE kind = ? AND key_hmac = ? AND attempted_at > ? ORDER BY attempted_at DESC LIMIT ?",
                (rs, row) -> rs.getObject(1, OffsetDateTime.class).toInstant(),
                kind,
                key,
                utc(since),
                max);
        if (recent.size() < max) {
            return OptionalLong.empty();
        }
        Instant lifts;
        if (fromNewest) {
            Instant newest = recent.get(0);
            if (Duration.between(recent.get(max - 1), newest).compareTo(window) >= 0) {
                return OptionalLong.empty();
            }
            lifts = newest.plus(window);
        } else {
            lifts = recent.get(max - 1).plus(window);
        }
        if (!lifts.isAfter(now)) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(Math.max(1, Duration.between(now, lifts).toSeconds() + 1));
    }

    private void insert(String kind, String key, Instant now) {
        jdbc.update(
                "INSERT INTO recycler_activation_attempt (id, kind, key_hmac, attempted_at) VALUES (?, ?, ?, ?)",
                IdGenerator.generate(),
                kind,
                key,
                utc(now));
    }
}
