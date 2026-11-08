package pe.esgtrazabilidad.collection.recycler.adapter.out.persistence;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface RecyclerStatusCopyJpaRepository extends JpaRepository<RecyclerStatusCopyEntity, UUID> {

    /**
     * One atomic statement: insert, or update only when the incoming instant is newer.
     * Two concurrent events for the same recycler cannot leave the older one winning.
     * Returns 1 when the row was inserted or updated, 0 when the event was old or repeated.
     */
    @Modifying
    @Query(
            value = """
                    INSERT INTO recycler_status_copy (recycler_id, association_id, status, last_occurred_at)
                    VALUES (:recyclerId, :associationId, :status, :occurredAt)
                    ON CONFLICT (recycler_id) DO UPDATE
                    SET association_id = EXCLUDED.association_id,
                        status = EXCLUDED.status,
                        last_occurred_at = EXCLUDED.last_occurred_at
                    WHERE recycler_status_copy.last_occurred_at < EXCLUDED.last_occurred_at
                    """,
            nativeQuery = true)
    int upsertIfNewer(
            @Param("recyclerId") UUID recyclerId,
            @Param("associationId") UUID associationId,
            @Param("status") String status,
            @Param("occurredAt") Instant occurredAt);

    @Query("select count(c) > 0 from RecyclerStatusCopyEntity c where c.recyclerId = :recyclerId and c.status = 'ACTIVE'")
    boolean isActive(@Param("recyclerId") UUID recyclerId);
}
