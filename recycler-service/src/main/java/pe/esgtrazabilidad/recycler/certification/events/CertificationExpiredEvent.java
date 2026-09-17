package pe.esgtrazabilidad.recycler.certification.events;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import pe.esgtrazabilidad.recycler.certification.domain.Certification;
import pe.esgtrazabilidad.kernel.events.DomainEvent;
import pe.esgtrazabilidad.kernel.id.IdGenerator;

public record CertificationExpiredEvent(
        UUID eventId, Instant occurredAt, UUID certificationId, UUID associationId, LocalDate expiredAt)
        implements DomainEvent {

    public static CertificationExpiredEvent from(Certification certification) {
        return new CertificationExpiredEvent(
                IdGenerator.generate(),
                Instant.now(),
                certification.getId(),
                certification.getAssociationId(),
                certification.getExpirationDate());
    }

    @Override
    public String routingKey() {
        return "certification.expired";
    }
}
