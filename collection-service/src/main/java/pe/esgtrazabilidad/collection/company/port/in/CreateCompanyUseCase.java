package pe.esgtrazabilidad.collection.company.port.in;

import pe.esgtrazabilidad.collection.company.domain.Company;

public interface CreateCompanyUseCase {

    Company create(CreateCompanyCommand command);
}
