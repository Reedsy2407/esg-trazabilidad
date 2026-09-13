package pe.esgtrazabilidad.recycler.certification.adapter.in.web;

import java.util.UUID;

import org.springframework.stereotype.Component;

import pe.esgtrazabilidad.recycler.certification.domain.Certification;
import pe.esgtrazabilidad.recycler.certification.port.in.CreateCertificationCommand;

@Component
class CertificationMapper {

    CreateCertificationCommand toCommand(UUID associationId, CreateCertificationRequest request) {
        return new CreateCertificationCommand(
                associationId, request.certificationType(), request.issuedDate(), request.expirationDate());
    }

    CertificationResponse toResponse(Certification certification) {
        return new CertificationResponse(
                certification.getId(),
                certification.getAssociationId(),
                certification.getCertificationType(),
                certification.getIssuedDate(),
                certification.getExpirationDate(),
                certification.isExpired());
    }
}
