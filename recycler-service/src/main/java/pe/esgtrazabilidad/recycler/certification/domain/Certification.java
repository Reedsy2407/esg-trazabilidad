package pe.esgtrazabilidad.recycler.certification.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import pe.esgtrazabilidad.kernel.id.IdGenerator;

public class Certification {

    private final UUID id;
    private final UUID associationId;
    private final String certificationType;
    private final LocalDate issuedDate;
    private LocalDate expirationDate;
    private Instant notifiedExpiredAt;

    private Certification(
            UUID id,
            UUID associationId,
            String certificationType,
            LocalDate issuedDate,
            LocalDate expirationDate,
            Instant notifiedExpiredAt) {
        this.id = id;
        this.associationId = associationId;
        this.certificationType = certificationType;
        this.issuedDate = issuedDate;
        this.expirationDate = expirationDate;
        this.notifiedExpiredAt = notifiedExpiredAt;
    }

    public static Certification create(
            UUID associationId, String certificationType, LocalDate issuedDate, LocalDate expirationDate) {
        if (issuedDate == null || expirationDate == null || !issuedDate.isBefore(expirationDate)) {
            throw new IllegalArgumentException("La fecha de emisión debe ser anterior a la fecha de vencimiento");
        }
        return new Certification(
                IdGenerator.generate(), associationId, certificationType, issuedDate, expirationDate, null);
    }

    public static Certification reconstruct(
            UUID id,
            UUID associationId,
            String certificationType,
            LocalDate issuedDate,
            LocalDate expirationDate,
            Instant notifiedExpiredAt) {
        return new Certification(id, associationId, certificationType, issuedDate, expirationDate, notifiedExpiredAt);
    }

    public boolean isExpired() {
        return expirationDate.isBefore(LocalDate.now());
    }

    public void renew(LocalDate newExpirationDate) {
        if (newExpirationDate == null || !issuedDate.isBefore(newExpirationDate)) {
            throw new IllegalArgumentException("La nueva fecha de vencimiento debe ser posterior a la fecha de emisión");
        }
        this.expirationDate = newExpirationDate;
        // Renewal is the expected normal-business resolution to an expired
        // certification, not an edge case -- without this reset, a
        // certification that expires, gets renewed, and later expires again
        // would never be re-notified (see Resolved Decisions in
        // SPEC-cross-service-events.md).
        this.notifiedExpiredAt = null;
    }

    /**
     * Only CertificationExpiryScanJob should call this -- marks that this
     * certification's expiry event has been published, so the job doesn't
     * re-notify it on every scan tick. renew() is what makes a certification
     * eligible to be notified again.
     */
    public void markNotifiedExpired(Instant notifiedAt) {
        this.notifiedExpiredAt = notifiedAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getAssociationId() {
        return associationId;
    }

    public String getCertificationType() {
        return certificationType;
    }

    public LocalDate getIssuedDate() {
        return issuedDate;
    }

    public LocalDate getExpirationDate() {
        return expirationDate;
    }

    public Instant getNotifiedExpiredAt() {
        return notifiedExpiredAt;
    }
}
