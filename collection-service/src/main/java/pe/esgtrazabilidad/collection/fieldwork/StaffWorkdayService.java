package pe.esgtrazabilidad.collection.fieldwork;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import pe.esgtrazabilidad.collection.evidence.EvidenceService;
import pe.esgtrazabilidad.collection.exception.CollectionErrors;
import pe.esgtrazabilidad.kernel.error.ApplicationException;

/**
 * What the staff sees of a recycler's field work (spec A.5c, CA-38, R28). Reading never writes. The signed
 * photo URL is built after the database reads have finished (no transaction, no lock: it is computed locally)
 * and is neither stored nor logged.
 */
@Service
public class StaffWorkdayService {

    static final int DEFAULT_POINTS_LIMIT = 5000;
    static final int MAX_POINTS_LIMIT = 20000;

    private final WorkdayReader reader;
    private final EvidenceService evidence;

    StaffWorkdayService(WorkdayReader reader, EvidenceService evidence) {
        this.reader = reader;
        this.evidence = evidence;
    }

    public record WorkdaySummary(
            UUID workdayId,
            Instant startedAt,
            Instant endedAt,
            String status,
            boolean withGps,
            long checkinCount,
            BigDecimal totalKg,
            long pointCount) {
    }

    public record WorkdaySummaryPage(List<WorkdaySummary> content, int page, int size, long totalElements, int totalPages) {
    }

    public record Point(long deviceSeq, Instant recordedAt, double lat, double lng, double accuracyM) {
    }

    public record RouteSummary(int pointCount, double distanceM, Instant firstAt, Instant lastAt, List<List<Double>> polyline) {
    }

    /** {@code state}: NONE (no photo), AVAILABLE (with a 5-minute {@code url}) or DELETED (by the retention). */
    public record Photo(String state, String url) {
    }

    public record Checkin(
            UUID checkinId,
            UUID recordId,
            Instant recordedAt,
            UUID neighborId,
            String neighborName,
            UUID scheduleId,
            BigDecimal weightKg,
            String wasteType,
            Double lat,
            Double lng,
            Double accuracyM,
            Photo photo) {
    }

    /**
     * {@code points} is the window {@code pointsOffset .. pointsOffset + points.size()} of {@code pointsTotal}
     * points (ordered by device sequence, then clock); empty after the retention summarised them.
     */
    public record WorkdayDetail(
            UUID workdayId,
            UUID recyclerId,
            UUID associationId,
            Instant startedAt,
            Instant endedAt,
            boolean withGps,
            List<Point> points,
            long pointsTotal,
            long pointsOffset,
            RouteSummary routeSummary,
            List<Checkin> checkins) {
    }

    public WorkdaySummaryPage list(UUID recyclerId, Instant from, Instant to, int page, int size) {
        WorkdayReader.WorkdayPage found = reader.page(recyclerId, from, to, page, size);
        int totalPages = (int) Math.ceil(found.total() / (double) size);
        return new WorkdaySummaryPage(
                found.rows().stream()
                        .map(r -> new WorkdaySummary(
                                r.workdayId(), r.startedAt(), r.endedAt(), r.status(), r.withGps(), r.checkinCount(), r.totalKg(), r.pointCount()))
                        .toList(),
                page,
                size,
                found.total(),
                totalPages);
    }

    /** @throws ApplicationException COL-016 when the workday does not exist */
    public WorkdayDetail detail(UUID workdayId, long pointsOffset, int pointsLimit) {
        WorkdayReader.WorkdayHeader header =
                reader.header(workdayId).orElseThrow(() -> new ApplicationException(CollectionErrors.WORKDAY_NOT_FOUND));
        List<Point> points = reader.points(workdayId, pointsOffset, pointsLimit).stream()
                .map(p -> new Point(p.deviceSeq(), p.recordedAt(), p.lat(), p.lng(), p.accuracyM()))
                .toList();
        long pointsTotal = reader.pointTotal(workdayId);
        RouteSummary summary = reader.summary(workdayId)
                .map(s -> new RouteSummary(s.pointCount(), s.distanceM(), s.firstAt(), s.lastAt(), s.polyline()))
                .orElse(null);
        List<Checkin> checkins = reader.checkins(workdayId).stream().map(this::withPhoto).toList();
        return new WorkdayDetail(
                header.workdayId(),
                header.recyclerId(),
                header.associationId(),
                header.startedAt(),
                header.endedAt(),
                header.withGps(),
                points,
                pointsTotal,
                pointsOffset,
                summary,
                checkins);
    }

    private Checkin withPhoto(WorkdayReader.CheckinRow row) {
        Photo photo;
        if (row.photoKey() == null) {
            photo = new Photo("NONE", null);
        } else if (row.photoDeleted()) {
            photo = new Photo("DELETED", null);
        } else {
            photo = new Photo("AVAILABLE", evidence.readUrl(row.photoKey()));
        }
        return new Checkin(
                row.checkinId(),
                row.recordId(),
                row.recordedAt(),
                row.neighborId(),
                row.neighborName(),
                row.scheduleId(),
                row.weightKg(),
                row.wasteType(),
                row.lat(),
                row.lng(),
                row.accuracyM(),
                photo);
    }
}
