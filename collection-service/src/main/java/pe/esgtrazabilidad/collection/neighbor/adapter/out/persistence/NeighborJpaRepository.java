package pe.esgtrazabilidad.collection.neighbor.adapter.out.persistence;

import java.time.Instant;
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
    @Query("SELECT n FROM NeighborEntity n WHERE n.id = :id AND n.deletedAt IS NULL")
    Optional<NeighborEntity> findByIdForShare(@Param("id") UUID id);

    Optional<NeighborEntity> findByIdAndDeletedAtIsNull(UUID id);

    // Compare-and-set on the status column: of two concurrent transitions the
    // second blocks on the row lock, then matches 0 rows. Runs inside the
    // caller's transaction (NeighborService's @Transactional methods).
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE NeighborEntity n SET n.status = :to WHERE n.id = :id AND n.status = :from AND n.deletedAt IS NULL")
    int updateStatusIfCurrent(@Param("id") UUID id, @Param("from") String from, @Param("to") String to);

    // Native: the deletion columns other than deleted_at aren't mapped on the
    // entity (nothing reads them through JPA).
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE neighbor SET deleted_at = :at, deleted_by_id = :actorId, deleted_by_email = :actorEmail, "
            + "deletion_reason = :reason WHERE id = :id AND deleted_at IS NULL", nativeQuery = true)
    int markDeleted(
            @Param("id") UUID id,
            @Param("at") Instant at,
            @Param("actorId") UUID actorId,
            @Param("actorEmail") String actorEmail,
            @Param("reason") String reason);
}
