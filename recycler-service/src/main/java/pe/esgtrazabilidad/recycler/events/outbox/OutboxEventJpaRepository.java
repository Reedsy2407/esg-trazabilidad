package pe.esgtrazabilidad.recycler.events.outbox;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

interface OutboxEventJpaRepository extends JpaRepository<OutboxEventEntity, UUID> {

    // id is a tiebreaker, not just a secondary sort key: it's a UUID v7 (time-
    // ordered, finer-grained than createdAt's Instant), so two rows written in
    // the same instant still get a strict, deterministic order -- required by
    // the dispatcher's ordering guarantee (see Task 39).
    @Query("SELECT e FROM OutboxEventEntity e WHERE e.status IN ('NEW', 'FAILED') ORDER BY e.createdAt ASC, e.id ASC")
    List<OutboxEventEntity> findPending(Pageable pageable);

    // @Modifying query methods aren't transactional just by being annotated --
    // they need an active transaction from the caller, which SimpleJpaRepository's
    // inherited save()/delete() get automatically but a custom query method does
    // not. @Transactional here (not on the adapter) keeps that concern at the
    // persistence boundary where it belongs.
    //
    // clearAutomatically = true: a bulk UPDATE runs directly against the
    // database and bypasses Hibernate's first-level cache, so within a single
    // transaction a findPending() called after this would otherwise return a
    // stale cached entity instead of the row this just updated. Real risk, not
    // just a test artifact: the whole Outbox pattern is built around a
    // publisher's save() and a later read/update sharing one transaction.
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("UPDATE OutboxEventEntity e SET e.status = :status WHERE e.id = :id")
    void updateStatus(@Param("id") UUID id, @Param("status") String status);
}
