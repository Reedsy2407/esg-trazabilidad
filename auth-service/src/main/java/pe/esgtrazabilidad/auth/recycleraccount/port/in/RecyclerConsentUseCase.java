package pe.esgtrazabilidad.auth.recycleraccount.port.in;

import java.util.UUID;

/** The consent a recycler reads and answers before any field route works (R8). */
public interface RecyclerConsentUseCase {

    Consent current();

    /**
     * Records the answer for the account. The new answer travels in the access token only from the next
     * refresh, which is when the app asks for it.
     *
     * @throws pe.esgtrazabilidad.kernel.error.ApplicationException if {@code version} is not the one in force
     */
    void answer(UUID accountId, int version, boolean accepted, boolean locationAccepted);

    record Consent(int version, String text, String locationText, int gpsDays, int photoMonths) {
    }
}
