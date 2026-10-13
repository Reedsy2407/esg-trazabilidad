package pe.esgtrazabilidad.reporting.sigersolmunicipal.service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import pe.esgtrazabilidad.reporting.sigersolmunicipal.DistrictStats;

/**
 * {@code sigersol_district_snapshot}: the long cache of SIGERSOL's annual counts and the "last good
 * value" when SIGERSOL stops answering (memory would not survive Render's free-tier sleep).
 */
@Repository
public class DistrictSnapshotStore {

    private final JdbcTemplate jdbc;

    DistrictSnapshotStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * The newest year stored for the district. Every method here runs in its own transaction: this
     * is read-only decoration that is called from inside the issue of a certificate, and a statement
     * the database refuses must not leave the certificate's transaction aborted.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<DistrictStats> latest(String ubigeo) {
        List<DistrictStats> rows = jdbc.query(
                "SELECT year, ubigeo, association_count, recycler_count, fetched_at FROM sigersol_district_snapshot "
                        + "WHERE ubigeo = ? ORDER BY year DESC LIMIT 1",
                (rs, rowNum) -> new DistrictStats(
                        rs.getInt("year"),
                        rs.getString("ubigeo"),
                        rs.getInt("association_count"),
                        rs.getInt("recycler_count"),
                        rs.getTimestamp("fetched_at").toLocalDateTime().toInstant(ZoneOffset.UTC)),
                ubigeo);
        return rows.stream().findFirst();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void save(DistrictStats stats) {
        jdbc.update(
                "INSERT INTO sigersol_district_snapshot (year, ubigeo, association_count, recycler_count, fetched_at) "
                        + "VALUES (?, ?, ?, ?, ?) ON CONFLICT (year, ubigeo) DO UPDATE SET "
                        + "association_count = EXCLUDED.association_count, recycler_count = EXCLUDED.recycler_count, "
                        + "fetched_at = EXCLUDED.fetched_at",
                stats.year(),
                stats.ubigeo(),
                stats.associationCount(),
                stats.recyclerCount(),
                LocalDateTime.ofInstant(stats.fetchedAt(), ZoneOffset.UTC));
    }
}
