package pe.esgtrazabilidad.auth.recycleraccount.service;

import java.time.Clock;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import pe.esgtrazabilidad.auth.recycleraccount.exception.RecyclerAccountErrors;
import pe.esgtrazabilidad.auth.recycleraccount.port.in.RecyclerConsentUseCase;
import pe.esgtrazabilidad.auth.recycleraccount.port.out.RecyclerAccountStore;
import pe.esgtrazabilidad.auth.retention.RetentionConfig;
import pe.esgtrazabilidad.auth.retention.RetentionConfigRepository;
import pe.esgtrazabilidad.kernel.error.ApplicationException;

@Service
class RecyclerConsentService implements RecyclerConsentUseCase {

    private static final Logger log = LoggerFactory.getLogger(RecyclerConsentService.class);

    private final RetentionConfigRepository retention;
    private final RecyclerAccountStore accounts;
    private final Clock clock;

    RecyclerConsentService(RetentionConfigRepository retention, RecyclerAccountStore accounts, Clock clock) {
        this.retention = retention;
        this.accounts = accounts;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Consent current() {
        RetentionConfig config = retention.get();
        return new Consent(
                config.consentVersion(),
                ConsentTexts.general(config),
                ConsentTexts.location(config),
                config.gpsDays(),
                config.photoMonths());
    }

    @Override
    @Transactional
    public void answer(UUID accountId, int version, boolean accepted, boolean locationAccepted) {
        if (version != retention.get().consentVersion()) {
            throw new ApplicationException(RecyclerAccountErrors.CONSENT_VERSION_OUTDATED);
        }
        // Refusing the general consent withdraws everything, location included: without it no field route works.
        accounts.recordConsent(accountId, accepted ? version : null, accepted && locationAccepted, clock.instant());
        log.info("event=recycler_consent accountId={} version={} accepted={} location={}", accountId, version, accepted, accepted && locationAccepted);
    }
}
