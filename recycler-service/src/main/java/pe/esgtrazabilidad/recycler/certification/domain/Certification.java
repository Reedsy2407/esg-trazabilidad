package pe.esgtrazabilidad.recycler.certification.domain;

import java.time.LocalDate;
import java.util.UUID;

import pe.esgtrazabilidad.kernel.id.IdGenerator;

public class Certification {

    private final UUID id;
    private final UUID associationId;
    private final String certificationType;
    private final LocalDate issuedDate;
    private LocalDate expirationDate;

    private Certification(
            UUID id, UUID associationId, String certificationType, LocalDate issuedDate, LocalDate expirationDate) {
        this.id = id;
        this.associationId = associationId;
        this.certificationType = certificationType;
        this.issuedDate = issuedDate;
        this.expirationDate = expirationDate;
    }

    public static Certification create(
            UUID associationId, String certificationType, LocalDate issuedDate, LocalDate expirationDate) {
        if (issuedDate == null || expirationDate == null || !issuedDate.isBefore(expirationDate)) {
            throw new IllegalArgumentException("La fecha de emisión debe ser anterior a la fecha de vencimiento");
        }
        return new Certification(
                IdGenerator.generate(), associationId, certificationType, issuedDate, expirationDate);
    }

    public static Certification reconstruct(
            UUID id, UUID associationId, String certificationType, LocalDate issuedDate, LocalDate expirationDate) {
        return new Certification(id, associationId, certificationType, issuedDate, expirationDate);
    }

    public boolean isExpired() {
        return expirationDate.isBefore(LocalDate.now());
    }

    public void renew(LocalDate newExpirationDate) {
        if (newExpirationDate == null || !issuedDate.isBefore(newExpirationDate)) {
            throw new IllegalArgumentException("La nueva fecha de vencimiento debe ser posterior a la fecha de emisión");
        }
        this.expirationDate = newExpirationDate;
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
}
