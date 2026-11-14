package pe.esgtrazabilidad.collection.retention;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * The retention periods this service applies: a local copy of what auth-service announces
 * ({@code RetentionConfigChanged}). Until the first event arrives the decided defaults hold (90 days of
 * exact GPS points, 12 months of photos). An event applies only if it is strictly newer than the stored
 * one, in one atomic statement, so an old, repeated or racing event always ends in the right state.
 */
@Component
public class RetentionSettingsCopy {

    /** The decided defaults, valid until auth-service announces anything. */
    public static final RetentionTerms DEFAULTS = new RetentionTerms(90, 12);

    /** The ranges auth-service accepts; an announcement outside them is discarded, never applied. */
    public static final int MIN_GPS_DAYS = 30;

    public static final int MAX_GPS_DAYS = 365;

    public static final int MIN_PHOTO_MONTHS = 3;

    public static final int MAX_PHOTO_MONTHS = 36;

    public record RetentionTerms(int gpsDays, int photoMonths) {
    }

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(RetentionSettingsCopy.class);

    private final NamedParameterJdbcTemplate jdbc;

    RetentionSettingsCopy(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public static boolean inRange(int gpsDays, int photoMonths) {
        return gpsDays >= MIN_GPS_DAYS
                && gpsDays <= MAX_GPS_DAYS
                && photoMonths >= MIN_PHOTO_MONTHS
                && photoMonths <= MAX_PHOTO_MONTHS;
    }

    /**
     * Applies the announced periods if {@code occurredAt} is strictly newer than the stored instant (or
     * nothing is stored yet).
     *
     * @return true if the copy changed
     */
    public boolean applyIfNewer(int gpsDays, int photoMonths, int consentVersion, Instant occurredAt) {
        // Postgres keeps microseconds: compare at that precision, or a redelivery of the very same event
        // (nanoseconds on the wire, truncated in the table) would look newer.
        Instant at = occurredAt.truncatedTo(ChronoUnit.MICROS);
        return jdbc.update(
                        """
                        insert into retention_settings_copy (id, gps_days, photo_months, consent_version, last_occurred_at)
                        values (1, :gps, :photos, :consent, :at)
                        on conflict (id) do update
                        set gps_days = excluded.gps_days, photo_months = excluded.photo_months,
                            consent_version = excluded.consent_version, last_occurred_at = excluded.last_occurred_at
                        where retention_settings_copy.last_occurred_at < excluded.last_occurred_at
                        """,
                        new MapSqlParameterSource("gps", gpsDays)
                                .addValue("photos", photoMonths)
                                .addValue("consent", consentVersion)
                                .addValue("at", Timestamp.from(at)))
                > 0;
    }

    /**
     * The periods in force: the last announced ones, or the defaults if none has arrived. The stored values are
     * checked again here (the table also has a CHECK): a period outside the decided ranges, however it got there,
     * must never reach the jobs that delete data, so the defaults hold instead.
     */
    public RetentionTerms current() {
        List<RetentionTerms> stored = jdbc.query(
                "select gps_days, photo_months from retention_settings_copy where id = 1",
                (rs, i) -> new RetentionTerms(rs.getInt("gps_days"), rs.getInt("photo_months")));
        return stored.isEmpty() ? DEFAULTS : validOrDefaults(stored.get(0));
    }

    static RetentionTerms validOrDefaults(RetentionTerms terms) {
        if (inRange(terms.gpsDays(), terms.photoMonths())) {
            return terms;
        }
        log.error("event=retention_terms_invalid gpsDays={} photoMonths={} action=use_defaults", terms.gpsDays(), terms.photoMonths());
        return DEFAULTS;
    }
}
