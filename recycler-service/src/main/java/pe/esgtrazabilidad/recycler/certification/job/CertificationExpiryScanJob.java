package pe.esgtrazabilidad.recycler.certification.job;

import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

import pe.esgtrazabilidad.recycler.certification.domain.Certification;
import pe.esgtrazabilidad.recycler.certification.port.out.CertificationRepository;

@Component
public class CertificationExpiryScanJob {

    private final CertificationRepository certificationRepository;
    private final CertificationExpiryProcessor processor;

    public CertificationExpiryScanJob(
            CertificationRepository certificationRepository, CertificationExpiryProcessor processor) {
        this.certificationRepository = certificationRepository;
        this.processor = processor;
    }

    @Scheduled(fixedDelayString = "${esg.certification.expiry-scan.interval-ms:3600000}")
    @SchedulerLock(name = "certificationExpiryScanJob", lockAtMostFor = "PT5M", lockAtLeastFor = "PT10S")
    public void scan() {
        List<Certification> due = certificationRepository.findExpiredAndNotYetNotified();
        for (Certification certification : due) {
            processor.process(certification);
        }
    }
}
