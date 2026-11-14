package pe.esgtrazabilidad.collection.retention;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Service;

import pe.esgtrazabilidad.collection.retention.RetentionSettingsCopy.RetentionTerms;

/**
 * Applies the retention periods in force (the last ones auth-service announced, or 90 days and 12 months).
 * One entry point for the scheduled job and for the on-demand run ({@code POST /retention/run}); both are
 * safe to repeat. A sleeping service simply runs it later: nothing depends on a fixed time. A bucket that is
 * down only delays the photos (their failures are counted, not thrown), never the GPS summaries; the other way
 * round, an unexpected exception in the GPS half would skip the photos for that run, and the next run does both.
 */
@Service
public class RetentionRunner {

    private final RetentionSettingsCopy settings;
    private final GpsRetention gps;
    private final PhotoRetention photos;
    private final Clock clock;

    RetentionRunner(RetentionSettingsCopy settings, GpsRetention gps, PhotoRetention photos, Clock clock) {
        this.settings = settings;
        this.gps = gps;
        this.photos = photos;
        this.clock = clock;
    }

    public record RetentionRunResult(
            int gpsDays, int photoMonths, int workdaysSummarized, int pointsDeleted, int photosDeleted, int photosFailed) {
    }

    public RetentionRunResult run() {
        return run(clock.instant());
    }

    /** Runs as if it were {@code now}: the cut-offs are computed from it (tests use this to control time). */
    public RetentionRunResult run(Instant now) {
        RetentionTerms terms = settings.current();
        GpsRetention.GpsResult gpsResult = gps.run(GpsRetention.cutoff(now, terms.gpsDays()), now);
        PhotoRetention.PhotoResult photoResult = photos.run(PhotoRetention.cutoff(now, terms.photoMonths()), now);
        return new RetentionRunResult(
                terms.gpsDays(),
                terms.photoMonths(),
                gpsResult.workdaysSummarized(),
                gpsResult.pointsDeleted(),
                photoResult.deleted(),
                photoResult.failed());
    }
}
