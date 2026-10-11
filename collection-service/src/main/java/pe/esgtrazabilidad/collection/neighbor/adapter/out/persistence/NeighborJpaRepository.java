package pe.esgtrazabilidad.collection.neighbor.adapter.out.persistence;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface NeighborJpaRepository
        extends JpaRepository<NeighborEntity, UUID>, JpaSpecificationExecutor<NeighborEntity> {

    // SELECT ... FOR SHARE: held until the caller's transaction ends, so a
    // concurrent status change (an UPDATE on this row) waits for it, and this
    // read waits for an uncommitted status change.
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("SELECT n FROM NeighborEntity n WHERE n.id = :id")
    Optional<NeighborEntity> findByIdForShare(@Param("id") UUID id);

    // Compare-and-set on the status column: of two concurrent transitions the
    // second blocks on the row lock, then matches 0 rows. Runs inside the
    // caller's transaction (NeighborService's @Transactional methods).
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE NeighborEntity n SET n.status = :to WHERE n.id = :id AND n.status = :from")
    int updateStatusIfCurrent(@Param("id") UUID id, @Param("from") String from, @Param("to") String to);
}
