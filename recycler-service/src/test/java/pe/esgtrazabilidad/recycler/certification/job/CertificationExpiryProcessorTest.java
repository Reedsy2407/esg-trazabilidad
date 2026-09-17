package pe.esgtrazabilidad.recycler.certification.job;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import pe.esgtrazabilidad.recycler.certification.domain.Certification;
import pe.esgtrazabilidad.recycler.certification.events.CertificationExpiredEvent;
import pe.esgtrazabilidad.recycler.certification.port.out.CertificationRepository;
import pe.esgtrazabilidad.recycler.events.publish.CertificationExpiredEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CertificationExpiryProcessorTest {

    @Mock
    private CertificationRepository certificationRepository;

    @Mock
    private CertificationExpiredEventPublisher publisher;

    @InjectMocks
    private CertificationExpiryProcessor processor;

    @Test
    void processPublishesTheEventAndPersistsTheNotifiedFlagInTheSameCall() {
        Certification certification = Certification.reconstruct(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "ISO 14001",
                LocalDate.now().minusDays(30),
                LocalDate.now().minusDays(1),
                null);

        processor.process(certification);

        ArgumentCaptor<CertificationExpiredEvent> eventCaptor = ArgumentCaptor.forClass(CertificationExpiredEvent.class);
        verify(publisher).publish(eventCaptor.capture());
        assertThat(eventCaptor.getValue().certificationId()).isEqualTo(certification.getId());
        assertThat(eventCaptor.getValue().associationId()).isEqualTo(certification.getAssociationId());

        ArgumentCaptor<Certification> updateCaptor = ArgumentCaptor.forClass(Certification.class);
        verify(certificationRepository).update(updateCaptor.capture());
        assertThat(updateCaptor.getValue().getNotifiedExpiredAt()).isNotNull();
        assertThat(certification.getNotifiedExpiredAt()).isNotNull();
    }
}
