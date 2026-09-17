package pe.esgtrazabilidad.recycler.certification.job;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import pe.esgtrazabilidad.recycler.certification.domain.Certification;
import pe.esgtrazabilidad.recycler.certification.port.out.CertificationRepository;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CertificationExpiryScanJobTest {

    @Mock
    private CertificationRepository certificationRepository;

    @Mock
    private CertificationExpiryProcessor processor;

    private CertificationExpiryScanJob job;

    private Certification newExpiredCertification() {
        return Certification.reconstruct(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "ISO 14001",
                LocalDate.now().minusDays(30),
                LocalDate.now().minusDays(1),
                null);
    }

    @Test
    void scanProcessesEveryExpiredAndNotYetNotifiedCertificationFound() {
        job = new CertificationExpiryScanJob(certificationRepository, processor);
        Certification first = newExpiredCertification();
        Certification second = newExpiredCertification();
        when(certificationRepository.findExpiredAndNotYetNotified()).thenReturn(List.of(first, second));

        job.scan();

        verify(processor, times(1)).process(first);
        verify(processor, times(1)).process(second);
    }

    @Test
    void scanDoesNothingWhenNoCertificationIsDue() {
        job = new CertificationExpiryScanJob(certificationRepository, processor);
        when(certificationRepository.findExpiredAndNotYetNotified()).thenReturn(List.of());

        job.scan();

        verifyNoInteractions(processor);
    }
}
