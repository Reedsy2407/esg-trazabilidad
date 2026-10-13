package pe.esgtrazabilidad.reporting.it.support;

/**
 * How many changesets each migration file holds, for the rollback ITs that undo "the last N".
 * Update {@link #V040} whenever a changeset is added to v0.4.0; the rollback ITs count from the end.
 */
public final class MigrationCounts {

    public static final int V030 = 4;
    public static final int V040 = 5;

    private MigrationCounts() {
    }
}
