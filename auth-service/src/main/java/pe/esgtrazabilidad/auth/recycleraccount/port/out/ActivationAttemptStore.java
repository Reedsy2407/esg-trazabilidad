package pe.esgtrazabilidad.auth.recycleraccount.port.out;

import java.time.Duration;
import java.time.Instant;
import java.util.OptionalLong;

/** The attempts the activation limits count. Keys are HMACs, never the address or the code itself. */
public interface ActivationAttemptStore {

    /**
     * Counts one attempt from this address unless it already used {@code max} in the last {@code window}.
     * Check and insert are atomic (two requests at once cannot both take the last slot). A refused
     * attempt is not recorded, so a client that keeps hammering does not extend its own block.
     *
     * @return empty if the attempt was let through, else the seconds to wait
     */
    OptionalLong takeAddressSlot(String addressHmac, Instant now, int max, Duration window);

    /** Seconds to wait if the code already failed {@code max} times in the last {@code block}; else empty. */
    OptionalLong codeBlockedFor(String codeHmac, Instant now, int max, Duration block);

    void recordCodeFailure(String codeHmac, Instant now);
}
