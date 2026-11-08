package pe.esgtrazabilidad.auth.recycleraccount.adapter.in.web;

import java.time.Instant;
import java.util.UUID;

import pe.esgtrazabilidad.auth.recycleraccount.domain.AccountStatus;
import pe.esgtrazabilidad.auth.recycleraccount.domain.AccountSummary;

/** Never carries a code: it exists in clear only in the answer that issued it (CA-5). */
record RecyclerAccountResponse(
        UUID accountId, UUID recyclerId, AccountStatus status, Instant activatedAt, int devices) {

    static RecyclerAccountResponse from(AccountSummary summary) {
        return new RecyclerAccountResponse(
                summary.accountId(), summary.recyclerId(), summary.status(), summary.activatedAt(), summary.devices());
    }
}
