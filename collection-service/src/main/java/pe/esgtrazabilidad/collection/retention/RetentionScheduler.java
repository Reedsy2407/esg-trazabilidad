package pe.esgtrazabilidad.collection.retention;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

/**
 * Once a day, at a quiet hour in Lima. The service may be asleep then: nothing depends on this firing (the
 * run is idempotent and {@code POST /retention/run} does the same on demand). ShedLock keeps two replicas
 * from running it together; the per-workday claim in {@link GpsRetention} is the second net.
 */
@Component
@ConditionalOnProperty(prefix = "esg.retention.job", name = "enabled", matchIfMissing = true)
class RetentionScheduler {

    private final RetentionRunner runner;

    RetentionScheduler(RetentionRunner runner) {
        this.runner = runner;
    }

    @Scheduled(cron = "${esg.retention.job.cron:0 30 3 * * *}", zone = "America/Lima")
    @SchedulerLock(name = "retentionJob", lockAtMostFor = "PT30M", lockAtLeastFor = "PT1M")
    void runDaily() {
        runner.run();
    }
}
