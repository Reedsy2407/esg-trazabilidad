package pe.esgtrazabilidad.collection.company.adapter.in.web;

import java.util.UUID;

import pe.esgtrazabilidad.collection.company.domain.CompanyStatus;

public record CompanyResponse(
        UUID id, String name, String ruc, String contactEmail, String contactPhone, String address, CompanyStatus status) {
}
