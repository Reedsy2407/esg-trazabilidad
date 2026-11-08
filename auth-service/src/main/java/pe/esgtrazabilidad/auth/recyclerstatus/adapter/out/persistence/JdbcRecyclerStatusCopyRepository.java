package pe.esgtrazabilidad.auth.recyclerstatus.adapter.out.persistence;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import pe.esgtrazabilidad.auth.recyclerstatus.domain.RecyclerStatus;
import pe.esgtrazabilidad.auth.recyclerstatus.domain.RecyclerStatusCopy;
import pe.esgtrazabilidad.auth.recyclerstatus.port.out.RecyclerStatusCopyRepository;

@Component
class JdbcRecyclerStatusCopyRepository implements RecyclerStatusCopyRepository {

    private final JdbcTemplate jdbc;

    JdbcRecyclerStatusCopyRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * One atomic statement: insert, or update only when the incoming instant is newer. Two
     * concurrent events for the same recycler cannot leave the older one winning.
     */
    @Override
    public boolean applyIfNewer(UUID recyclerId, UUID associationId, RecyclerStatus status, Instant occurredAt) {
        // Postgres keeps microseconds: compare at that precision, or a redelivery of the very same
        // event (nanoseconds on the wire, truncated in the table) would look newer.
        Instant at = occurredAt.truncatedTo(ChronoUnit.MICROS);
        int changed = jdbc.update(
                """
                INSERT INTO recycler_status_copy_auth (recycler_id, association_id, status, last_occurred_at)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (recycler_id) DO UPDATE
                SET association_id = EXCLUDED.association_id,
                    status = EXCLUDED.status,
                    last_occurred_at = EXCLUDED.last_occurred_at
                WHERE recycler_status_copy_auth.last_occurred_at < EXCLUDED.last_occurred_at
                """,
                recyclerId,
                associationId,
                status.name(),
                OffsetDateTime.ofInstant(at, ZoneOffset.UTC));
        return changed > 0;
    }

    @Override
    public Optional<RecyclerStatusCopy> find(UUID recyclerId) {
        return jdbc
                .query(
                        "SELECT recycler_id, association_id, status, last_occurred_at"
                                + " FROM recycler_status_copy_auth WHERE recycler_id = ?",
                        (rs, row) -> new RecyclerStatusCopy(
                                rs.getObject(1, UUID.class),
                                rs.getObject(2, UUID.class),
                                RecyclerStatus.valueOf(rs.getString(3)),
                                rs.getObject(4, OffsetDateTime.class).toInstant()),
                        recyclerId)
                .stream()
                .findFirst();
    }
}
