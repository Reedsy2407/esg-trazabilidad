package pe.esgtrazabilidad.collection.company.adapter.in.web;

import org.springframework.stereotype.Component;

import pe.esgtrazabilidad.collection.company.domain.Company;
import pe.esgtrazabilidad.collection.company.port.in.CreateCompanyCommand;

@Component
class CompanyMapper {

    CreateCompanyCommand toCommand(CreateCompanyRequest request) {
        return new CreateCompanyCommand(
                request.name(), request.ruc(), request.contactEmail(), request.contactPhone(), request.address());
    }

    CompanyResponse toResponse(Company company) {
        return new CompanyResponse(
                company.getId(),
                company.getName(),
                company.getRuc(),
                company.getContactEmail(),
                company.getContactPhone(),
                company.getAddress(),
                company.getStatus());
    }
}
