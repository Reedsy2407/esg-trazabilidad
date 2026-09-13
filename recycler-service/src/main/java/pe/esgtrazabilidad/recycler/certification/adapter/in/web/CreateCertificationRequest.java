package pe.esgtrazabilidad.recycler.certification.adapter.in.web;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateCertificationRequest(
        @NotBlank String certificationType, @NotNull LocalDate issuedDate, @NotNull LocalDate expirationDate) {
}
