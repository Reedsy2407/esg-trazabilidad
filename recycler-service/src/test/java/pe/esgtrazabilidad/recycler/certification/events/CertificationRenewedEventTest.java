package pe.esgtrazabilidad.recycler.certification.events;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import pe.esgtrazabilidad.recycler.certification.domain.Certification;

import static org.assertj.core.api.Assertions.assertThat;

class CertificationRenewedEventTest {

    @Test
    void fromMapsEveryFieldFromTheRenewedCertificationAndGeneratesEventMetadata() {
        Certification certification = Certification.create(
                UUID.randomUUID(), "ISO 14001", LocalDate.now().minusDays(30), LocalDate.now().plusDays(1));
        LocalDate newExpirationDate = LocalDate.now().plusYears(1);
        certification.renew(newExpirationDate);

        CertificationRenewedEvent event = CertificationRenewedEvent.from(certification);

        assertThat(event.eventId()).isNotNull();
        assertThat(event.occurredAt()).isNotNull();
        assertThat(event.certificationId()).isEqualTo(certification.getId());
        assertThat(event.associationId()).isEqualTo(certification.getAssociationId());
        assertThat(event.newExpirationDate()).isEqualTo(newExpirationDate);
        assertThat(event.routingKey()).isEqualTo("certification.renewed");
    }
}
