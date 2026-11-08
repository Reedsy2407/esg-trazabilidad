package pe.esgtrazabilidad.auth.recycleraccount.port.in;

/** The public route a recycler uses once per phone: trade the code handed over in person for a session. */
public interface RecyclerActivationUseCase {

    /**
     * @param clientAddress the caller's address as resolved by the trusted-proxy rule; it is only ever
     *     counted by its HMAC
     */
    ActivationResult activate(String code, String deviceLabel, String deviceInstallId, String clientAddress);

    /**
     * {@code refreshToken} is in clear only here. {@code consentRequired} is false only when this account
     * already accepted the consent version in force (a phone activated again after a revocation).
     */
    record ActivationResult(
            String accessToken, String refreshToken, long expiresIn, boolean consentRequired, int consentVersion) {

        @Override
        public String toString() {
            return "ActivationResult[consentRequired=" + consentRequired + ", consentVersion=" + consentVersion + "]";
        }
    }
}
