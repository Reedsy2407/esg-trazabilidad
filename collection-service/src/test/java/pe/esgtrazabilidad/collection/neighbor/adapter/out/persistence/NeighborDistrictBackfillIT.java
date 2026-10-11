package pe.esgtrazabilidad.collection.neighbor.adapter.out.persistence;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the v0.1.8 backfill SQL -- the same file the Liquibase changeset
 * runs -- against pre-v3 rows inserted with a null ubigeo, on real Postgres.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class NeighborDistrictBackfillIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void matchesFreeTextDistrictsWithoutCaseAccentsOrExtraSpacesAndLeavesTheRestNull() throws IOException {
        UUID comas = insertPreV3("Comas");
        UUID rimac = insertPreV3("RÍMAC");
        UUID smp = insertPreV3("  san   martin de porres ");
        UUID brena = insertPreV3("breña");
        UUID callao = insertPreV3("Mi Peru");
        UUID surco = insertPreV3("Surco");
        UUID none = insertPreV3(null);

        runBackfill();

        assertThat(ubigeoOf(comas)).isEqualTo("150110");
        assertThat(ubigeoOf(rimac)).isEqualTo("150128");
        assertThat(ubigeoOf(smp)).isEqualTo("150135");
        assertThat(ubigeoOf(brena)).isEqualTo("150105");
        assertThat(ubigeoOf(callao)).isEqualTo("070107");
        assertThat(ubigeoOf(surco)).isNull();
        assertThat(ubigeoOf(none)).isNull();
    }

    @Test
    void isIdempotentAndNeverOverwritesAnExistingUbigeo() throws IOException {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO neighbor (id, full_name, address, district, district_ubigeo, status) "
                        + "VALUES (?, 'Ana', 'Jr. 1', 'Comas', '150112', 'ACTIVE')",
                id);

        runBackfill();
        runBackfill();

        assertThat(ubigeoOf(id)).isEqualTo("150112");
    }

    private UUID insertPreV3(String district) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO neighbor (id, full_name, address, district, status) VALUES (?, 'Vecino', 'Jr. 1', ?, 'ACTIVE')",
                id, district);
        return id;
    }

    private void runBackfill() throws IOException {
        String sql = new ClassPathResource("db/changelog/sql/v0.1.8_backfill_neighbor_district_ubigeo.sql")
                .getContentAsString(StandardCharsets.UTF_8);
        jdbcTemplate.execute(sql);
    }

    private String ubigeoOf(UUID id) {
        return jdbcTemplate.queryForObject("SELECT district_ubigeo FROM neighbor WHERE id = ?", String.class, id);
    }
}
