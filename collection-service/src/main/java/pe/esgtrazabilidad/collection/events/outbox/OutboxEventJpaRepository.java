package pe.esgtrazabilidad.collection.events.outbox;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

interface OutboxEventJpaRepository extends JpaRepository<OutboxEventEntity, UUID> {

    @Query("SELECT e FROM OutboxEventEntity e WHERE e.status IN ('NEW', 'FAILED') ORDER BY e.createdAt ASC")
    List<OutboxEventEntity> findPending(Pageable pageable);

    // @Modifying query methods aren't transactional just by being annotated --
    // they need an active transaction from the caller, which SimpleJpaRepository's
    // inherited save()/delete() get automatically but a custom query method does
    // not. @Transactional here (not on the adapter) keeps that concern at the
    // persistence boundary where it belongs.
    @Transactional
    @Modifying
    @Query("UPDATE OutboxEventEntity e SET e.status = :status WHERE e.id = :id")
    void updateStatus(@Param("id") UUID id, @Param("status") String status);
}
