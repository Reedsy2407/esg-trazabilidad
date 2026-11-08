package pe.esgtrazabilidad.auth.recycleraccount.adapter.in.web;

import jakarta.validation.constraints.NotNull;

/** A.2: {version, accepted, locationAccepted}. All three are required: no answer is guessed. */
record ConsentRequest(@NotNull Integer version, @NotNull Boolean accepted, @NotNull Boolean locationAccepted) {
}
