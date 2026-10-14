package pe.esgtrazabilidad.reporting.sigersolmunicipal.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import pe.esgtrazabilidad.reporting.sigersolmunicipal.DistrictContext;
import pe.esgtrazabilidad.reporting.sigersolmunicipal.DistrictStats;
import pe.esgtrazabilidad.reporting.sigersolmunicipal.SigersolMunicipalStats;

/**
 * The district context a screen or a certificate shows. SIGERSOL's data is annual, so a snapshot is
 * trusted for {@link #REFRESH_AFTER}; after that the provider is asked again. The default year is
 * the latest one with rows, probing down from the previous calendar year. Whatever happens, this
 * never throws and never blocks anything: with no answer and no snapshot the result is
 * "no disponible".
 */
@Service
public class DistrictContextService {

    static final Duration REFRESH_AFTER = Duration.ofDays(30);
    /**
     * How many calendar years the probe tries, counting back from last year (2025 to 2020 in 2026).
     * SIGERSOL has rows from 2019, but the latest year with rows is what matters and it is 2024.
     */
    static final int PROBE_YEARS = 6;
    /** Short on purpose: it spares slow calls during an outage, not the data's own freshness. */
    static final Duration NEGATIVE_CACHE = Duration.ofMinutes(5);
    /** More districts than Lima has (about 50) with no answer at once would be a bug, not a workload. */
    static final int NEGATIVE_CACHE_MAX_ENTRIES = 256;

    private static final Logger log = LoggerFactory.getLogger(DistrictContextService.class);

    private final SigersolMunicipalStats provider;
    private final DistrictSnapshotStore store;
    private final Clock clock;
    private final Map<String, Instant> noAnswerUntil = new ConcurrentHashMap<>();

    @Autowired
    DistrictContextService(SigersolMunicipalStats provider, DistrictSnapshotStore store) {
        this(provider, store, Clock.systemUTC());
    }

    DistrictContextService(SigersolMunicipalStats provider, DistrictSnapshotStore store, Clock clock) {
        this.provider = provider;
        this.store = store;
        this.clock = clock;
    }

    public DistrictContext contextFor(String ubigeo) {
        Optional<DistrictStats> snapshot;
        try {
            snapshot = store.latest(ubigeo);
        } catch (RuntimeException e) {
            log.warn("SIGERSOL snapshot unreadable: {}", e.getClass().getSimpleName());
            return DistrictContext.unavailable(ubigeo);
        }
        if (snapshot.isPresent() && isFresh(snapshot.get())) {
            return DistrictContext.of(snapshot.get(), false);
        }
        boolean skipped = recentlyEmpty(ubigeo);
        Optional<DistrictStats> fetched = skipped ? Optional.empty() : fetchLatestYear(ubigeo);
        if (fetched.isPresent()) {
            return DistrictContext.of(fetched.get(), false);
        }
        if (!skipped) {
            // Only a real miss starts the five minutes: serving from the cache must not extend it,
            // or a district asked for every minute would never be tried again.
            rememberNoAnswer(ubigeo);
        }
        // No answer worth keeping: the last good value, marked as such, or nothing.
        return snapshot.map(stats -> DistrictContext.of(stats, true)).orElseGet(() -> DistrictContext.unavailable(ubigeo));
    }

    /**
     * Any failure (an adapter that breaks its contract, a value the table refuses) is treated as an
     * outage: the context is read-only decoration and must never stop a certificate.
     */
    private Optional<DistrictStats> fetchLatestYear(String ubigeo) {
        int latestYear = clock.instant().atZone(ZoneOffset.UTC).getYear() - 1;
        try {
            return probe(ubigeo, latestYear);
        } catch (RuntimeException e) {
            log.warn("SIGERSOL context failed: {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private Optional<DistrictStats> probe(String ubigeo, int latestYear) {
        for (int year = latestYear; year > latestYear - PROBE_YEARS; year--) {
            SigersolMunicipalStats.Answer answer = provider.fetch(year, ubigeo);
            if (answer instanceof SigersolMunicipalStats.Answer.Found found) {
                DistrictStats stats = new DistrictStats(
                        year, ubigeo, found.associationCount(), found.recyclerCount(), clock.instant());
                store.save(stats);
                return Optional.of(stats);
            }
            if (answer instanceof SigersolMunicipalStats.Answer.Unavailable) {
                // An outage is not a reason to keep probing older years.
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    /** A district that just had no answer (or no rows) is not asked again for {@link #NEGATIVE_CACHE}. */
    private boolean recentlyEmpty(String ubigeo) {
        Instant until = noAnswerUntil.get(ubigeo);
        if (until == null) {
            return false;
        }
        if (until.isAfter(clock.instant())) {
            return true;
        }
        noAnswerUntil.remove(ubigeo, until);
        return false;
    }

    /**
     * Notes the miss, first purging every entry that already expired and, if live ones still fill
     * the cap, dropping the one closest to expiring: the map can never grow past the cap.
     */
    private void rememberNoAnswer(String ubigeo) {
        Instant now = clock.instant();
        noAnswerUntil.values().removeIf(until -> !until.isAfter(now));
        while (noAnswerUntil.size() >= NEGATIVE_CACHE_MAX_ENTRIES && !noAnswerUntil.containsKey(ubigeo)) {
            noAnswerUntil.entrySet().stream()
                    .min(Map.Entry.comparingByValue())
                    .ifPresent(oldest -> noAnswerUntil.remove(oldest.getKey(), oldest.getValue()));
        }
        noAnswerUntil.put(ubigeo, now.plus(NEGATIVE_CACHE));
    }

    /** Entries currently held, expired or not (tests only). */
    int negativeCacheSize() {
        return noAnswerUntil.size();
    }

    private boolean isFresh(DistrictStats stats) {
        return stats.fetchedAt().isAfter(Instant.now(clock).minus(REFRESH_AFTER));
    }
}
