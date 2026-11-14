package pe.esgtrazabilidad.auth.events.outbox;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

interface OutboxEventJpaRepository extends JpaRepository<OutboxEventEntity, UUID> {

    // id is a tiebreaker: a UUID v7 (time-ordered, finer than createdAt), so two rows written in the same
    // instant still have a strict, deterministic order (the dispatcher's ordering guarantee).
    @Query("SELECT e FROM OutboxEventEntity e WHERE e.status IN ('NEW', 'FAILED') ORDER BY e.createdAt ASC, e.id ASC")
    List<OutboxEventEntity> findPending(Pageable pageable);

    // A bulk UPDATE needs its own transaction (a custom query method does not get one), and bypasses the
    // first-level cache, hence clearAutomatically.
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("UPDATE OutboxEventEntity e SET e.status = :status WHERE e.id = :id")
    void updateStatus(@Param("id") UUID id, @Param("status") String status);
}
