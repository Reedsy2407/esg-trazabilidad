package pe.esgtrazabilidad.reporting.certificate.adapter.in.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Why the certificate is annulled. Who comes from the token, never from here. */
public record AnnulCertificateRequest(@NotBlank @Size(max = 500) String reason) {
}
