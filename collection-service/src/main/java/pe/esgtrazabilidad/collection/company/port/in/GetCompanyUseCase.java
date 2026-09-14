package pe.esgtrazabilidad.collection.company.port.in;

import java.util.UUID;

import pe.esgtrazabilidad.collection.company.domain.Company;

public interface GetCompanyUseCase {

    Company getById(UUID id);
}
