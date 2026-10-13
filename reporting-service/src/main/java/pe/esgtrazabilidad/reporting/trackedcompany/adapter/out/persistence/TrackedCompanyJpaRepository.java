package pe.esgtrazabilidad.reporting.trackedcompany.adapter.out.persistence;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Every read leaves out logically deleted companies; every write is one
 * conditional UPDATE (the row is never loaded and saved back: the entity is
 * insert-only).
 */
interface TrackedCompanyJpaRepository extends JpaRepository<TrackedCompanyEntity, UUID> {

    Optional<TrackedCompanyEntity> findByIdAndDeletedAtIsNull(UUID id);

    Optional<TrackedCompanyEntity> findByRucAndDeletedAtIsNull(String ruc);

    Page<TrackedCompanyEntity> findByDeletedAtIsNull(Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE TrackedCompanyEntity c SET c.name = :name, c.verification = :verification, "
            + "c.sunatEstado = :estado, c.sunatCondicion = :condicion, c.sunatCheckedAt = :checkedAt "
            + "WHERE c.id = :id AND c.deletedAt IS NULL")
    int updateSunatData(
            @Param("id") UUID id,
            @Param("name") String name,
            @Param("verification") String verification,
            @Param("estado") String estado,
            @Param("condicion") String condicion,
            @Param("checkedAt") Instant checkedAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE TrackedCompanyEntity c SET c.contactName = :name, c.contactEmail = :email, "
            + "c.contactPhone = :phone WHERE c.id = :id AND c.deletedAt IS NULL")
    int updateContact(
            @Param("id") UUID id,
            @Param("name") String name,
            @Param("email") String email,
            @Param("phone") String phone);

    @Query("SELECT COUNT(c) > 0 FROM EsgCertificateEntity c WHERE c.trackedCompanyId = :id")
    boolean hasCertificates(@Param("id") UUID id);

    /** RUC and association only change while no certificate (annulled ones included) exists. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE TrackedCompanyEntity c
            SET c.ruc = :ruc, c.associationId = :associationId, c.name = :name,
                c.verification = :verification, c.sunatEstado = :estado,
                c.sunatCondicion = :condicion, c.sunatCheckedAt = :checkedAt
            WHERE c.id = :id AND c.deletedAt IS NULL
              AND NOT EXISTS (SELECT 1 FROM EsgCertificateEntity e WHERE e.trackedCompanyId = :id)
            """)
    int changeIdentityIfNoCertificates(
            @Param("id") UUID id,
            @Param("ruc") String ruc,
            @Param("associationId") UUID associationId,
            @Param("name") String name,
            @Param("verification") String verification,
            @Param("estado") String estado,
            @Param("condicion") String condicion,
            @Param("checkedAt") Instant checkedAt);

    /** Of two concurrent changes exactly one finds the row still in {@code from}. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE TrackedCompanyEntity c SET c.status = :to "
            + "WHERE c.id = :id AND c.status = :from AND c.deletedAt IS NULL")
    int changeStatusIfCurrent(@Param("id") UUID id, @Param("from") String from, @Param("to") String to);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE TrackedCompanyEntity c
            SET c.deletedAt = :at, c.deletedById = :actorId, c.deletedByEmail = :actorEmail,
                c.deletionReason = :reason
            WHERE c.id = :id AND c.deletedAt IS NULL
              AND NOT EXISTS (SELECT 1 FROM EsgCertificateEntity e WHERE e.trackedCompanyId = :id)
            """)
    int deleteIfNoCertificates(
            @Param("id") UUID id,
            @Param("reason") String reason,
            @Param("actorId") UUID actorId,
            @Param("actorEmail") String actorEmail,
            @Param("at") Instant at);
}
