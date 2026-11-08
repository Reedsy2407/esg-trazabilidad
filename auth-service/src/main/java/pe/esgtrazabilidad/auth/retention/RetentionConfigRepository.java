package pe.esgtrazabilidad.auth.retention;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** The single {@code retention_config} row (id = 1), seeded by the migration. */
@Repository
public class RetentionConfigRepository {

    private final JdbcTemplate jdbc;

    RetentionConfigRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public RetentionConfig get() {
        return jdbc.queryForObject(
                "SELECT gps_days, photo_months, consent_version FROM retention_config WHERE id = 1",
                (rs, row) -> new RetentionConfig(rs.getInt(1), rs.getInt(2), rs.getInt(3)));
    }

    /**
     * Stores the new periods in one statement. The consent version goes up by one only if a period really
     * changed, so saving the same values asks nobody again. Returns what is stored afterwards.
     */
    public RetentionConfig update(int gpsDays, int photoMonths, Instant now) {
        return jdbc.queryForObject(
                """
                UPDATE retention_config
                SET consent_version = consent_version + CASE WHEN gps_days <> ? OR photo_months <> ? THEN 1 ELSE 0 END,
                    gps_days = ?, photo_months = ?, updated_at = ?
                WHERE id = 1
                RETURNING gps_days, photo_months, consent_version
                """,
                (rs, row) -> new RetentionConfig(rs.getInt(1), rs.getInt(2), rs.getInt(3)),
                gpsDays,
                photoMonths,
                gpsDays,
                photoMonths,
                OffsetDateTime.ofInstant(now, ZoneOffset.UTC));
    }
}
