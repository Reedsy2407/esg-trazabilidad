package pe.esgtrazabilidad.reporting.trackedcompany.adapter.out.persistence;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import pe.esgtrazabilidad.kernel.id.IdGenerator;
import pe.esgtrazabilidad.kernel.security.CurrentActor;
import pe.esgtrazabilidad.reporting.trackedcompany.domain.CompanyContact;
import pe.esgtrazabilidad.reporting.trackedcompany.domain.TrackedCompany;
import pe.esgtrazabilidad.reporting.trackedcompany.domain.TrackedCompanyVerification;
import pe.esgtrazabilidad.reporting.trackedcompany.domain.TrackedCompanyStatus;
import pe.esgtrazabilidad.reporting.trackedcompany.port.out.TrackedCompanyRepository;

@Component
class TrackedCompanyRepositoryAdapter implements TrackedCompanyRepository {

    private final TrackedCompanyJpaRepository jpaRepository;
    private final JdbcTemplate jdbc;

    TrackedCompanyRepositoryAdapter(TrackedCompanyJpaRepository jpaRepository, JdbcTemplate jdbc) {
        this.jpaRepository = jpaRepository;
        this.jdbc = jdbc;
    }

    @Override
    public TrackedCompany save(TrackedCompany trackedCompany) {
        return toDomain(jpaRepository.save(toEntity(trackedCompany)));
    }

    @Override
    @Transactional
    public boolean updateSunatData(
            UUID id, String name, boolean verified, String estado, String condicion, Instant checkedAt) {
        String verification = (verified ? TrackedCompanyVerification.VERIFIED : TrackedCompanyVerification.UNVERIFIED).name();
        return jpaRepository.updateSunatData(id, name, verification, estado, condicion, checkedAt) == 1;
    }

    @Override
    @Transactional
    public boolean updateContact(UUID id, CompanyContact contact) {
        return jpaRepository.updateContact(id, contact.name(), contact.email(), contact.phone()) == 1;
    }

    @Override
    public boolean hasCertificates(UUID id) {
        return jpaRepository.hasCertificates(id);
    }

    @Override
    @Transactional
    public boolean changeIdentityIfNoCertificates(
            UUID id,
            String ruc,
            UUID associationId,
            String name,
            boolean verified,
            String estado,
            String condicion,
            Instant checkedAt) {
        String verification = (verified ? TrackedCompanyVerification.VERIFIED : TrackedCompanyVerification.UNVERIFIED).name();
        return jpaRepository.changeIdentityIfNoCertificates(
                        id, ruc, associationId, name, verification, estado, condicion, checkedAt)
                == 1;
    }

    /** The conditional UPDATE decides; the history row is written only by the call that won it. */
    @Override
    @Transactional
    public boolean changeStatus(
            UUID id, TrackedCompanyStatus from, TrackedCompanyStatus to, String reason, CurrentActor actor, Instant at) {
        if (jpaRepository.changeStatusIfCurrent(id, from.name(), to.name()) != 1) {
            return false;
        }
        jdbc.update(
                "INSERT INTO tracked_company_status_change "
                        + "(id, tracked_company_id, from_status, to_status, reason, actor_id, actor_email, changed_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                IdGenerator.generate(),
                id,
                from.name(),
                to.name(),
                reason,
                actor.id(),
                actor.email(),
                LocalDateTime.ofInstant(at, ZoneOffset.UTC));
        return true;
    }

    @Override
    @Transactional
    public boolean deleteIfNoCertificates(UUID id, String reason, CurrentActor actor, Instant at) {
        return jpaRepository.deleteIfNoCertificates(id, reason, actor.id(), actor.email(), at) == 1;
    }

    @Override
    public Optional<TrackedCompany> findById(UUID id) {
        return jpaRepository.findByIdAndDeletedAtIsNull(id).map(this::toDomain);
    }

    @Override
    public Optional<TrackedCompany> findByRuc(String ruc) {
        return jpaRepository.findByRucAndDeletedAtIsNull(ruc).map(this::toDomain);
    }

    @Override
    public Page<TrackedCompany> findAll(Pageable pageable) {
        return jpaRepository.findByDeletedAtIsNull(pageable).map(this::toDomain);
    }

    private TrackedCompanyEntity toEntity(TrackedCompany trackedCompany) {
        return new TrackedCompanyEntity(
                trackedCompany.getId(),
                trackedCompany.getName(),
                trackedCompany.getRuc(),
                trackedCompany.getAssociationId(),
                trackedCompany.getStatus().name(),
                trackedCompany.getVerification().name(),
                trackedCompany.getSunatEstado(),
                trackedCompany.getSunatCondicion(),
                trackedCompany.getSunatCheckedAt(),
                trackedCompany.getContact().name(),
                trackedCompany.getContact().email(),
                trackedCompany.getContact().phone());
    }

    private TrackedCompany toDomain(TrackedCompanyEntity entity) {
        return TrackedCompany.reconstruct(
                entity.getId(),
                entity.getName(),
                entity.getRuc(),
                entity.getAssociationId(),
                TrackedCompanyStatus.valueOf(entity.getStatus()),
                TrackedCompanyVerification.valueOf(entity.getVerification()),
                entity.getSunatEstado(),
                entity.getSunatCondicion(),
                entity.getSunatCheckedAt(),
                new CompanyContact(entity.getContactName(), entity.getContactEmail(), entity.getContactPhone()));
    }
}
