package pe.esgtrazabilidad.reporting.certificate.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import pe.esgtrazabilidad.reporting.trackedcompany.domain.TrackedCompany;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EsgCertificateTest {

    private TrackedCompany sampleTrackedCompany() {
        return TrackedCompany.create("Empresa de prueba", "20123456789", UUID.randomUUID());
    }

    @Test
    void issuesACertificateFreezingTheTrackedCompanysCurrentValues() {
        TrackedCompany trackedCompany = sampleTrackedCompany();

        EsgCertificate certificate = EsgCertificate.issue(
                trackedCompany,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31),
                new BigDecimal("120.50"),
                new BigDecimal("85.00"));

        assertThat(certificate.getId()).isNotNull();
        assertThat(certificate.getTrackedCompanyId()).isEqualTo(trackedCompany.getId());
        assertThat(certificate.getAssociationId()).isEqualTo(trackedCompany.getAssociationId());
        assertThat(certificate.getCompanyName()).isEqualTo(trackedCompany.getName());
        assertThat(certificate.getCompanyRuc()).isEqualTo(trackedCompany.getRuc());
        assertThat(certificate.getKilosTrazados()).isEqualByComparingTo("120.50");
        assertThat(certificate.getHierarchyCompliancePercent()).isEqualByComparingTo("85.00");
        assertThat(certificate.getIssuedAt()).isNotNull();
        assertThat(certificate.getStatus()).isEqualTo(EsgCertificateStatus.ISSUED);
        assertThat(certificate.isAnnulled()).isFalse();
        assertThat(certificate.getAnnulledAt()).isNull();
        assertThat(certificate.getAnnulmentReason()).isNull();
    }

    @Test
    void rejectsAPeriodEndBeforePeriodStart() {
        assertThatThrownBy(() -> EsgCertificate.issue(
                        sampleTrackedCompany(),
                        LocalDate.of(2026, 1, 31),
                        LocalDate.of(2026, 1, 1),
                        BigDecimal.ZERO,
                        BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reconstructRebuildsAnExistingCertificateWithoutGeneratingANewId() {
        UUID id = UUID.randomUUID();
        UUID trackedCompanyId = UUID.randomUUID();
        UUID associationId = UUID.randomUUID();

        EsgCertificate certificate = EsgCertificate.reconstruct(
                id,
                trackedCompanyId,
                associationId,
                "Empresa",
                "20123456789",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31),
                new BigDecimal("10.00"),
                new BigDecimal("50.00"),
                java.time.Instant.parse("2026-02-01T00:00:00Z"),
                EsgCertificateStatus.ISSUED,
                null,
                null,
                null,
                null);

        assertThat(certificate.getId()).isEqualTo(id);
        assertThat(certificate.getTrackedCompanyId()).isEqualTo(trackedCompanyId);
        assertThat(certificate.getAssociationId()).isEqualTo(associationId);
    }

    @Test
    void reconstructRebuildsAnAnnulledCertificateKeepingItsFrozenNumbers() {
        UUID actorId = UUID.randomUUID();
        java.time.Instant annulledAt = java.time.Instant.parse("2026-03-01T10:00:00Z");

        EsgCertificate certificate = EsgCertificate.reconstruct(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Empresa",
                "20123456789",
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 1, 31),
                new BigDecimal("10.00"),
                new BigDecimal("50.00"),
                java.time.Instant.parse("2026-02-01T00:00:00Z"),
                EsgCertificateStatus.ANNULLED,
                annulledAt,
                actorId,
                "ana@example.com",
                "Datos erróneos");

        assertThat(certificate.isAnnulled()).isTrue();
        assertThat(certificate.getAnnulledAt()).isEqualTo(annulledAt);
        assertThat(certificate.getAnnulledById()).isEqualTo(actorId);
        assertThat(certificate.getAnnulledByEmail()).isEqualTo("ana@example.com");
        assertThat(certificate.getAnnulmentReason()).isEqualTo("Datos erróneos");
        assertThat(certificate.getKilosTrazados()).isEqualByComparingTo("10.00");
    }
}
