package pe.esgtrazabilidad.auth.recycleraccount.adapter.in.web;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/** Only the recycler: the association and the status are never taken from the caller. */
record CreateRecyclerAccountRequest(@NotNull UUID recyclerId) {
}
