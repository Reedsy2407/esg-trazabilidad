package pe.esgtrazabilidad.auth.recycleraccount.adapter.in.web;

import pe.esgtrazabilidad.auth.recycleraccount.port.in.RecyclerSessionUseCase.RefreshResult;

record RefreshResponse(String accessToken, String refreshToken, long expiresIn) {

    static RefreshResponse from(RefreshResult result) {
        return new RefreshResponse(result.accessToken(), result.refreshToken(), result.expiresIn());
    }

    @Override
    public String toString() {
        return "RefreshResponse[expiresIn=" + expiresIn + "]";
    }
}
