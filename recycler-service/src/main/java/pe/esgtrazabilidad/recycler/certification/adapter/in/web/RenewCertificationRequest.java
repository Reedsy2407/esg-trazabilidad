package pe.esgtrazabilidad.recycler.certification.adapter.in.web;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;

public record RenewCertificationRequest(@NotNull LocalDate newExpirationDate) {
}
