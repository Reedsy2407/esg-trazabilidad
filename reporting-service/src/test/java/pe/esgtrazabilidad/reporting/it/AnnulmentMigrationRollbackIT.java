package pe.esgtrazabilidad.reporting.it;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;

import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import pe.esgtrazabilidad.reporting.it.support.MigrationCounts;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * v0.2.0 (certificate annulment) is additive and reversible: rolling it back
 * restores the unconditional period exclusion and drops the annulment columns,
 * and applying it again works. Plain Liquibase against a real Postgres, no
 * Spring context.
 */
@Testcontainers
class AnnulmentMigrationRollbackIT {

    private static final String CHANGELOG = "db/changelog/db.changelog-master.yaml";

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void rollsBackAndReappliesTheAnnulmentChangesets() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
            Liquibase liquibase = new Liquibase(
                    CHANGELOG,
                    new ClassLoaderResourceAccessor(),
                    DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection)));

            liquibase.update();
            assertThat(columnExists(connection, "status")).isTrue();
            assertThat(exclusionIsPartial(connection)).isTrue();

            // The two v0.2.0 changesets sit under the v0.3.0 and v0.4.0 ones: undo all of them.
            liquibase.rollback(2 + MigrationCounts.V030 + MigrationCounts.V040, null);
            assertThat(columnExists(connection, "status")).isFalse();
            assertThat(columnExists(connection, "annulment_reason")).isFalse();
            assertThat(exclusionIsPartial(connection)).isFalse();
            assertThat(exclusionExists(connection)).isTrue();

            liquibase.update();
            assertThat(columnExists(connection, "status")).isTrue();
            assertThat(exclusionIsPartial(connection)).isTrue();
        }
    }

    private static boolean columnExists(Connection connection, String column) throws Exception {
        try (Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery("SELECT count(*) FROM information_schema.columns "
                        + "WHERE table_name = 'esg_certificate' AND column_name = '" + column + "'")) {
            rs.next();
            return rs.getInt(1) == 1;
        }
    }

    private static boolean exclusionExists(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery(
                        "SELECT count(*) FROM pg_constraint WHERE conname = 'excl_esg_certificate_company_period'")) {
            rs.next();
            return rs.getInt(1) == 1;
        }
    }

    private static boolean exclusionIsPartial(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery(
                        "SELECT pg_get_constraintdef(oid) FROM pg_constraint "
                                + "WHERE conname = 'excl_esg_certificate_company_period'")) {
            return rs.next() && rs.getString(1).contains("WHERE");
        }
    }
}
