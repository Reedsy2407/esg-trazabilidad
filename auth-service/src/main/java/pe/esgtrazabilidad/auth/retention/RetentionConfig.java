package pe.esgtrazabilidad.auth.retention;

/**
 * The retention periods the recycler's consent shows (GPS in days, photos in months) and the
 * consent version they belong to. Stored as a single row; only an ADMIN changes it, and a change
 * raises {@code consentVersion} so the next refresh asks the recycler again.
 */
public record RetentionConfig(int gpsDays, int photoMonths, int consentVersion) {
}
