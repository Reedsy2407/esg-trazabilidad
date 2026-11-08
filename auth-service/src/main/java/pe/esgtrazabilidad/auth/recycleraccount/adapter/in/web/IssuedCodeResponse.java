package pe.esgtrazabilidad.auth.recycleraccount.adapter.in.web;

import java.time.Instant;
import java.util.UUID;

import pe.esgtrazabilidad.auth.recycleraccount.domain.IssuedCode;

record IssuedCodeResponse(UUID accountId, String activationCode, Instant expiresAt) {

    static IssuedCodeResponse from(IssuedCode code) {
        return new IssuedCodeResponse(code.accountId(), code.activationCode(), code.expiresAt());
    }

    @Override
    public String toString() {
        return "IssuedCodeResponse[accountId=" + accountId + ", expiresAt=" + expiresAt + "]";
    }
}
