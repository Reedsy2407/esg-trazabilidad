package pe.esgtrazabilidad.auth.recycleraccount.adapter.in.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The code is validated only for size here: its content is judged by the service (always AUTH-014). */
record ActivateRequest(
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 80) String deviceLabel,
        @NotBlank @Size(max = 100) String deviceInstallId) {

    @Override
    public String toString() {
        return "ActivateRequest[deviceLabel=" + deviceLabel + "]";
    }
}
