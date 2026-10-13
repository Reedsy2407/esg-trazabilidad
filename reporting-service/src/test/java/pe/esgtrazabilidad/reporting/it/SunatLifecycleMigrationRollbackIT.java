package pe.esgtrazabilidad.reporting.it;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;

import org.junit.jupiter.api.Test;

import pe.esgtrazabilidad.reporting.it.support.MigrationCounts;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * v0.3.0 (SUNAT data, contact, lifecycle) is additive and reversible: its four
 * changesets are the last ones applied, existing companies become UNVERIFIED,
 * rolling back restores the plain RUC constraint, and applying again works.
 * Plain Liquibase against a real Postgres, no Spring context.
 */
@Testcontainers
class SunatLifecycleMigrationRollbackIT {

    private static final String CHANGELOG = "db/changelog/db.changelog-master.yaml";
    private static final int V030_CHANGESETS = MigrationCounts.V030;
    /** v0.4.0 comes after v0.3.0: it is applied (and rolled back) around it. */
    private static final int V040_CHANGESETS = MigrationCounts.V040;

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void existingCompaniesBecomeUnverifiedAndTheChangesRollBackAndReapply() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
            Liquibase liquibase = new Liquibase(
                    CHANGELOG,
                    new ClassLoaderResourceAccessor(),
                    DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection)));

            // A company that exists before v0.3.0: apply everything up to v0.2.0, insert, then the rest.
            liquibase.update(
                    liquibase.listUnrunChangeSets(null, null).size() - V030_CHANGESETS - V040_CHANGESETS, (String) null);
            execute(connection, "INSERT INTO tracked_company (id, name, ruc, association_id, status) "
                    + "VALUES ('0192f3a8-0000-7000-8000-0000000000aa', 'Escrita a mano', '20999999990', "
                    + "'0192f3a8-0000-7000-8000-0000000000bb', 'ACTIVE')");
            liquibase.update();

            assertThat(scalar(connection, "SELECT verification FROM tracked_company")).isEqualTo("UNVERIFIED");
            assertThat(scalar(connection, "SELECT count(*) FROM pg_indexes WHERE indexname = 'uq_tracked_company_ruc_active'"))
                    .isEqualTo("1");
            assertThat(scalar(connection, "SELECT count(*) FROM pg_constraint WHERE conname = 'tracked_company_ruc_key'"))
                    .isEqualTo("0");

            // The RUC is free again once a company is logically deleted.
            execute(connection, "UPDATE tracked_company SET deleted_at = now(), "
                    + "deleted_by_id = '0192f3a8-0000-7000-8000-0000000000cc', deletion_reason = 'duplicada'");
            execute(connection, "INSERT INTO tracked_company (id, name, ruc, association_id, status) "
                    + "VALUES ('0192f3a8-0000-7000-8000-0000000000dd', 'Otra', '20999999990', "
                    + "'0192f3a8-0000-7000-8000-0000000000bb', 'ACTIVE')");
            // The deletion columns only make sense together.
            assertThrows(
                    SQLException.class,
                    () -> execute(connection, "UPDATE tracked_company SET deleted_at = now() "
                            + "WHERE id = '0192f3a8-0000-7000-8000-0000000000dd'"));
            connection.rollback(); // Liquibase's connection is not auto-commit: clear the aborted transaction
            execute(connection, "DELETE FROM tracked_company WHERE id = '0192f3a8-0000-7000-8000-0000000000dd'");

            liquibase.rollback(V030_CHANGESETS + V040_CHANGESETS, null);
            assertThat(scalar(connection, "SELECT count(*) FROM information_schema.columns "
                    + "WHERE table_name = 'tracked_company' AND column_name IN ('verification', 'deleted_at', 'contact_email')"))
                    .isEqualTo("0");
            assertThat(scalar(connection, "SELECT count(*) FROM pg_constraint WHERE conname = 'tracked_company_ruc_key'"))
                    .isEqualTo("1");
            assertThat(scalar(connection, "SELECT count(*) FROM information_schema.tables "
                    + "WHERE table_name IN ('ruc_lookup_cache', 'tracked_company_status_change')"))
                    .isEqualTo("0");

            liquibase.update();
            assertThat(scalar(connection, "SELECT verification FROM tracked_company")).isEqualTo("UNVERIFIED");
        }
    }

    private static void execute(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
        if (!connection.getAutoCommit()) {
            connection.commit();
        }
    }

    private static String scalar(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery(sql)) {
            rs.next();
            return rs.getString(1);
        }
    }
}
