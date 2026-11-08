package pe.esgtrazabilidad.auth.recycleraccount.adapter.in.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Used by refresh and by logout. {@code toString} hides the token so it can never reach a log. */
record RefreshRequest(@NotBlank @Size(max = 128) String refreshToken) {

    @Override
    public String toString() {
        return "RefreshRequest[]";
    }
}
