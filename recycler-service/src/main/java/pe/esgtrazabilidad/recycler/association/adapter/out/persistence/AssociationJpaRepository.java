package pe.esgtrazabilidad.recycler.association.adapter.out.persistence;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

interface AssociationJpaRepository
        extends JpaRepository<AssociationEntity, UUID>, JpaSpecificationExecutor<AssociationEntity> {

    Optional<AssociationEntity> findByRuc(String ruc);

    // clearAutomatically = true: same reasoning as OutboxEventJpaRepository's
    // updateStatus (Task 27/29) -- a bulk UPDATE bypasses Hibernate's first-level
    // cache, so a later read sharing this transaction would otherwise see a
    // stale cached total. @Transactional: @Modifying methods aren't
    // transactional just by being annotated.
    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("UPDATE AssociationEntity a SET a.totalKilosCollected = a.totalKilosCollected + :amount WHERE a.id = :id")
    void incrementTotalKilos(@Param("id") UUID id, @Param("amount") BigDecimal amount);
}
