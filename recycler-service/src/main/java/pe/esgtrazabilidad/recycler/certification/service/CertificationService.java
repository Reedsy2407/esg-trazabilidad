package pe.esgtrazabilidad.recycler.certification.service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import pe.esgtrazabilidad.kernel.error.ApplicationException;
import pe.esgtrazabilidad.recycler.association.port.out.AssociationRepository;
import pe.esgtrazabilidad.recycler.certification.domain.Certification;
import pe.esgtrazabilidad.recycler.certification.exception.CertificationErrors;
import pe.esgtrazabilidad.recycler.certification.port.in.CreateCertificationCommand;
import pe.esgtrazabilidad.recycler.certification.port.in.CreateCertificationUseCase;
import pe.esgtrazabilidad.recycler.certification.port.in.GetCertificationUseCase;
import pe.esgtrazabilidad.recycler.certification.port.in.ListCertificationsUseCase;
import pe.esgtrazabilidad.recycler.certification.port.out.CertificationRepository;

@Service
class CertificationService implements CreateCertificationUseCase, GetCertificationUseCase, ListCertificationsUseCase {

    private final CertificationRepository certificationRepository;
    private final AssociationRepository associationRepository;

    CertificationService(CertificationRepository certificationRepository, AssociationRepository associationRepository) {
        this.certificationRepository = certificationRepository;
        this.associationRepository = associationRepository;
    }

    @Override
    public Certification create(CreateCertificationCommand command) {
        if (associationRepository.findById(command.associationId()).isEmpty()) {
            throw new ApplicationException(CertificationErrors.ASSOCIATION_NOT_FOUND);
        }
        if (command.issuedDate() == null
                || command.expirationDate() == null
                || !command.issuedDate().isBefore(command.expirationDate())) {
            throw new ApplicationException(CertificationErrors.INVALID_DATE_RANGE);
        }
        Certification certification = Certification.create(
                command.associationId(), command.certificationType(), command.issuedDate(), command.expirationDate());
        return certificationRepository.save(certification);
    }

    @Override
    public Certification getById(UUID associationId, UUID id) {
        Certification certification = certificationRepository
                .findById(id)
                .orElseThrow(() -> new ApplicationException(CertificationErrors.NOT_FOUND));
        if (!certification.getAssociationId().equals(associationId)) {
            throw new ApplicationException(CertificationErrors.NOT_FOUND);
        }
        return certification;
    }

    @Override
    public Page<Certification> list(UUID associationId, Pageable pageable) {
        return certificationRepository.findAll(associationId, pageable);
    }
}
