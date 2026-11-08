package pe.esgtrazabilidad.auth.recycleraccount.port.in;

import java.util.UUID;

/** Keeping a recycler's session alive (rotating refresh) and ending it. */
public interface RecyclerSessionUseCase {

    /**
     * Rotates the refresh token (A.3). The previous token keeps working for 10 minutes and, if presented
     * inside that window, is answered with the same current token; outside it the device is revoked.
     */
    RefreshResult refresh(String refreshToken);

    /** Revokes the device that token belongs to, if it is one of the caller's own. Idempotent. */
    void logout(UUID accountId, String refreshToken);

    record RefreshResult(String accessToken, String refreshToken, long expiresIn) {

        @Override
        public String toString() {
            return "RefreshResult[expiresIn=" + expiresIn + "]";
        }
    }
}
