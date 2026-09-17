package pe.esgtrazabilidad.recycler.certification.events;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import pe.esgtrazabilidad.recycler.certification.domain.Certification;

import static org.assertj.core.api.Assertions.assertThat;

class CertificationExpiredEventTest {

    @Test
    void fromMapsEveryFieldFromTheCertificationAndGeneratesEventMetadata() {
        Certification certification = Certification.reconstruct(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "ISO 14001",
                LocalDate.now().minusDays(30),
                LocalDate.now().minusDays(1),
                null);

        CertificationExpiredEvent event = CertificationExpiredEvent.from(certification);

        assertThat(event.eventId()).isNotNull();
        assertThat(event.occurredAt()).isNotNull();
        assertThat(event.certificationId()).isEqualTo(certification.getId());
        assertThat(event.associationId()).isEqualTo(certification.getAssociationId());
        assertThat(event.expiredAt()).isEqualTo(certification.getExpirationDate());
        assertThat(event.routingKey()).isEqualTo("certification.expired");
    }
}
