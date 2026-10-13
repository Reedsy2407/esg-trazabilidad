package pe.esgtrazabilidad.reporting.sunat.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import pe.esgtrazabilidad.kernel.ruc.Ruc;
import pe.esgtrazabilidad.reporting.sunat.RucLookup;
import pe.esgtrazabilidad.reporting.sunat.RucLookupResult;

/**
 * The lookup the services use: the provider behind a 24-hour cache in the
 * {@code ruc_lookup_cache} table (memory would not survive Render's free-tier
 * sleep, and the provider's monthly quota is small). Found and NotFound are
 * cached; Unavailable never is, so a provider outage is retried on the next
 * request.
 *
 * <p>{@link #lookupFresh} skips the cache read but still stores a Found/NotFound
 * answer: it is for the explicit "Verificar con SUNAT" and for the unverified
 * fallback's re-check, where a day-old answer would be wrong.
 */
@Service
public class CachedRucLookup {

    static final Duration TTL = Duration.ofHours(24);

    private final RucLookup provider;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    @Autowired
    CachedRucLookup(RucLookup provider, JdbcTemplate jdbc) {
        this(provider, jdbc, Clock.systemUTC());
    }

    CachedRucLookup(RucLookup provider, JdbcTemplate jdbc, Clock clock) {
        this.provider = provider;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public RucLookupResult lookup(Ruc ruc) {
        RucLookupResult cached = readFresh(ruc);
        return cached != null ? cached : lookupFresh(ruc);
    }

    public RucLookupResult lookupFresh(Ruc ruc) {
        RucLookupResult result = provider.lookup(ruc);
        if (!(result instanceof RucLookupResult.Unavailable)) {
            store(ruc, result);
        }
        return result;
    }

    private RucLookupResult readFresh(Ruc ruc) {
        LocalDateTime oldest = utc(clock.instant().minus(TTL));
        List<RucLookupResult> rows = jdbc.query(
                "SELECT outcome, legal_name, estado, condicion, ubigeo FROM ruc_lookup_cache "
                        + "WHERE ruc = ? AND fetched_at > ?",
                (rs, rowNum) -> "FOUND".equals(rs.getString("outcome"))
                        ? (RucLookupResult) new RucLookupResult.Found(
                                rs.getString("legal_name"),
                                rs.getString("estado"),
                                rs.getString("condicion"),
                                rs.getString("ubigeo"))
                        : new RucLookupResult.NotFound(),
                ruc.value(),
                oldest);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private void store(Ruc ruc, RucLookupResult result) {
        LocalDateTime now = utc(clock.instant());
        String sql = "INSERT INTO ruc_lookup_cache (ruc, outcome, legal_name, estado, condicion, ubigeo, fetched_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT (ruc) DO UPDATE SET outcome = EXCLUDED.outcome, "
                + "legal_name = EXCLUDED.legal_name, estado = EXCLUDED.estado, condicion = EXCLUDED.condicion, "
                + "ubigeo = EXCLUDED.ubigeo, fetched_at = EXCLUDED.fetched_at";
        if (result instanceof RucLookupResult.Found found) {
            jdbc.update(sql, ruc.value(), "FOUND", found.legalName(), found.estado(), found.condicion(),
                    found.ubigeo(), now);
        } else {
            jdbc.update(sql, ruc.value(), "NOT_FOUND", null, null, null, null, now);
        }
    }

    private static LocalDateTime utc(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
