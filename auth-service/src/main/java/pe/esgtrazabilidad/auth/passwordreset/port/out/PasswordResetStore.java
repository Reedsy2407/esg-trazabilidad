package pe.esgtrazabilidad.auth.passwordreset.port.out;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Everything password recovery keeps in the database: the codes (as HMACs) and what the limits count.
 * The limits live here, not in memory, because Render puts the service to sleep and restarts it.
 */
public interface PasswordResetStore {

    enum Kind { REQUEST, CONFIRM, NOTICE }

    record LiveCode(UUID id, String codeHmac) {}

    enum Outcome { MATCH, MISMATCH, NONE }

    record CheckResult(Outcome outcome, UUID codeId) {}

    /** Drops request rows and spent or expired codes older than {@code cutoff}. */
    void purgeOlderThan(Instant cutoff);

    /** When each accepted attempt of this kind from this address happened since {@code since}. */
    List<Instant> acceptedByIpSince(Kind kind, String ipHmac, Instant since);

    /** When each accepted attempt of this kind for this email happened since {@code since}. */
    List<Instant> acceptedByEmailSince(Kind kind, String emailHmac, Instant since);

    /** Emails that really left since {@code since}, of every kind: the daily cap counts these. */
    int sentSince(Instant since);

    UUID recordRequest(Kind kind, String emailHmac, String ipHmac, Instant at, boolean accepted);

    void markSent(UUID requestId);

    /** Spends the account's live code (if any) and stores the new one, in one transaction. */
    void replaceCode(UUID codeId, UUID staffUserId, String codeHmac, Instant now, Instant expiresAt);

    /** The same statements as {@link #replaceCode}, matching no row: for requests that send nothing. */
    void replaceCodeDecoy();

    /**
     * Looks at the account's live, unexpired code under a row lock and asks {@code matches} whether the
     * typed code is the one. A mismatch counts an attempt and spends the code on the {@code maxAttempts}th.
     */
    CheckResult check(UUID staffUserId, Instant now, int maxAttempts, Predicate<LiveCode> matches);

    /** Spends the code if it is still live; false means somebody else spent it first. */
    boolean consume(UUID codeId, Instant now);
}
