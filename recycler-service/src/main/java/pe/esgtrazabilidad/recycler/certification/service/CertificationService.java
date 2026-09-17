package pe.esgtrazabilidad.recycler.certification.service;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.esgtrazabilidad.kernel.error.ApplicationException;
import pe.esgtrazabilidad.recycler.association.port.out.AssociationRepository;
import pe.esgtrazabilidad.recycler.certification.domain.Certification;
import pe.esgtrazabilidad.recycler.certification.events.CertificationRenewedEvent;
import pe.esgtrazabilidad.recycler.certification.exception.CertificationErrors;
import pe.esgtrazabilidad.recycler.certification.port.in.CreateCertificationCommand;
import pe.esgtrazabilidad.recycler.certification.port.in.CreateCertificationUseCase;
import pe.esgtrazabilidad.recycler.certification.port.in.GetCertificationUseCase;
import pe.esgtrazabilidad.recycler.certification.port.in.ListCertificationsUseCase;
import pe.esgtrazabilidad.recycler.certification.port.in.RenewCertificationUseCase;
import pe.esgtrazabilidad.recycler.certification.port.out.CertificationRepository;
import pe.esgtrazabilidad.recycler.events.publish.CertificationRenewedEventPublisher;

@Service
class CertificationService
        implements CreateCertificationUseCase,
                GetCertificationUseCase,
                ListCertificationsUseCase,
                RenewCertificationUseCase {

    private final CertificationRepository certificationRepository;
    private final AssociationRepository associationRepository;
    private final CertificationRenewedEventPublisher eventPublisher;

    CertificationService(
            CertificationRepository certificationRepository,
            AssociationRepository associationRepository,
            CertificationRenewedEventPublisher eventPublisher) {
        this.certificationRepository = certificationRepository;
        this.associationRepository = associationRepository;
        this.eventPublisher = eventPublisher;
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
        return findScoped(associationId, id);
    }

    @Override
    public Page<Certification> list(UUID associationId, Pageable pageable) {
        return certificationRepository.findAll(associationId, pageable);
    }

    // The outbox write (inside eventPublisher.publish()) must land in the same
    // DB transaction as certificationRepository.update() -- neither call gets
    // one on its own otherwise, since each is a separate SimpleJpaRepository
    // method.
    @Override
    @Transactional
    public Certification renew(UUID associationId, UUID id, LocalDate newExpirationDate) {
        Certification certification = findScoped(associationId, id);
        try {
            certification.renew(newExpirationDate);
        } catch (IllegalArgumentException e) {
            throw new ApplicationException(CertificationErrors.INVALID_DATE_RANGE);
        }
        Certification updated = certificationRepository.update(certification);
        eventPublisher.publish(CertificationRenewedEvent.from(updated));
        return updated;
    }

    private Certification findScoped(UUID associationId, UUID id) {
        Certification certification = certificationRepository
                .findById(id)
                .orElseThrow(() -> new ApplicationException(CertificationErrors.NOT_FOUND));
        if (!certification.getAssociationId().equals(associationId)) {
            throw new ApplicationException(CertificationErrors.NOT_FOUND);
        }
        return certification;
    }
}
