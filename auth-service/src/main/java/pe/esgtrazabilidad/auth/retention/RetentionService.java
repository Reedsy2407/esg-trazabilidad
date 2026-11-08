package pe.esgtrazabilidad.auth.retention;

import java.time.Clock;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.esgtrazabilidad.auth.exception.AuthErrors;
import pe.esgtrazabilidad.auth.staffuser.domain.StaffUser;
import pe.esgtrazabilidad.auth.staffuser.port.out.StaffUserRepository;
import pe.esgtrazabilidad.kernel.error.ApplicationException;

/**
 * Changing how long GPS and photos are kept. Only an ADMIN may, decided from the database like every other
 * admin-only action (a deactivated or demoted admin loses the power at once). A real change raises the
 * consent version, so each recycler's next refresh omits {@code cv} until they accept the new text (CA-15).
 *
 * <p>The specification fixes who may change the periods but gives this service no route for it, so this
 * use case has no HTTP entry point yet; see the closing report.
 */
@Service
public class RetentionService {

    /** Defensive bounds, not business values: a typo must not store "0 days" or ten thousand years. */
    public static final int MAX_GPS_DAYS = 3650;

    public static final int MAX_PHOTO_MONTHS = 120;

    private static final Logger log = LoggerFactory.getLogger(RetentionService.class);

    private final RetentionConfigRepository repository;
    private final StaffUserRepository staffUsers;
    private final Clock clock;

    RetentionService(RetentionConfigRepository repository, StaffUserRepository staffUsers, Clock clock) {
        this.repository = repository;
        this.staffUsers = staffUsers;
        this.clock = clock;
    }

    @Transactional
    public RetentionConfig update(UUID actorId, int gpsDays, int photoMonths) {
        boolean admin = actorId != null
                && staffUsers.findById(actorId).filter(StaffUser::isActive).filter(StaffUser::isAdmin).isPresent();
        if (!admin) {
            throw new ApplicationException(AuthErrors.ADMIN_REQUIRED);
        }
        if (gpsDays < 1 || gpsDays > MAX_GPS_DAYS || photoMonths < 1 || photoMonths > MAX_PHOTO_MONTHS) {
            throw new IllegalArgumentException("Retention period out of range");
        }
        RetentionConfig after = repository.update(gpsDays, photoMonths, clock.instant());
        log.info(
                "event=retention_changed actor={} gpsDays={} photoMonths={} consentVersion={}",
                actorId,
                after.gpsDays(),
                after.photoMonths(),
                after.consentVersion());
        return after;
    }
}
