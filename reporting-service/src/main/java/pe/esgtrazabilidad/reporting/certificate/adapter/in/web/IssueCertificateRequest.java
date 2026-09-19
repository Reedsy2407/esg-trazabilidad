package pe.esgtrazabilidad.reporting.certificate.adapter.in.web;

import java.time.LocalDate;

import jakarta.validation.constraints.NotNull;

public record IssueCertificateRequest(@NotNull LocalDate periodStart, @NotNull LocalDate periodEnd) {
}
