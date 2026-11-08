package pe.esgtrazabilidad.auth.recycleraccount.adapter.in.web;

import pe.esgtrazabilidad.auth.recycleraccount.port.in.RecyclerActivationUseCase.ActivationResult;

record ActivationResponse(
        String accessToken, String refreshToken, long expiresIn, boolean consentRequired, int consentVersion) {

    static ActivationResponse from(ActivationResult result) {
        return new ActivationResponse(
                result.accessToken(),
                result.refreshToken(),
                result.expiresIn(),
                result.consentRequired(),
                result.consentVersion());
    }

    @Override
    public String toString() {
        return "ActivationResponse[consentRequired=" + consentRequired + "]";
    }
}
