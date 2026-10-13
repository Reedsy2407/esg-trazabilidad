package pe.esgtrazabilidad.reporting.sigersolmunicipal.service;

import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import pe.esgtrazabilidad.reporting.it.support.FakeSigersolMunicipal;
import pe.esgtrazabilidad.reporting.sigersolmunicipal.DistrictContext;
import pe.esgtrazabilidad.reporting.sigersolmunicipal.DistrictStats;
import pe.esgtrazabilidad.reporting.sigersolmunicipal.SigersolMunicipalStats.Answer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The snapshot against a real Postgres: what is fetched is stored once and then served without
 * asking SIGERSOL again, an upsert replaces the same year, and the table refuses nonsense.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class DistrictContextServiceIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private DistrictContextService service;

    @Autowired
    private DistrictSnapshotStore store;

    @Autowired
    private FakeSigersolMunicipal sigersol;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void reset() {
        sigersol.reset();
    }

    private static int lastYear() {
        return Instant.now().atZone(ZoneOffset.UTC).getYear() - 1;
    }

    @Test
    void whatIsFetchedIsStoredAndTheSecondQuestionDoesNotReachSigersol() {
        sigersol.answer(lastYear(), "150131", new Answer.Found(5, 62));

        DistrictContext first = service.contextFor("150131");
        int callsAfterFirst = sigersol.calls();
        DistrictContext second = service.contextFor("150131");

        assertThat(first).extracting("year", "associationCount", "recyclerCount").containsExactly(lastYear(), 5, 62);
        assertThat(second).isEqualTo(first);
        assertThat(sigersol.calls()).isEqualTo(callsAfterFirst);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM sigersol_district_snapshot WHERE ubigeo = '150131'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void anOutageWithNothingStoredReadsNoDisponibleAndStoresNothing() {
        sigersol.unavailableAll(true);

        assertThat(service.contextFor("150132").status()).isEqualTo(DistrictContext.Status.UNAVAILABLE);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM sigersol_district_snapshot WHERE ubigeo = '150132'", Integer.class))
                .isZero();
    }

    @Test
    void anOldSnapshotSurvivesAnOutageAsTheLastGoodValue() {
        store.save(new DistrictStats(2023, "150133", 2, 20, Instant.now().minusSeconds(60L * 86_400)));
        sigersol.unavailableAll(true);

        DistrictContext context = service.contextFor("150133");

        assertThat(context.status()).isEqualTo(DistrictContext.Status.AVAILABLE);
        assertThat(context).extracting("year", "associationCount", "stale").containsExactly(2023, 2, true);
    }

    @Test
    void savingTheSameYearAndDistrictReplacesTheCounts() {
        store.save(new DistrictStats(2024, "150134", 1, 10, Instant.now()));
        store.save(new DistrictStats(2024, "150134", 9, 90, Instant.now()));

        assertThat(store.latest("150134")).get().extracting("associationCount", "recyclerCount").containsExactly(9, 90);
    }

    @Test
    void theTableRefusesAMalformedDistrictOrNegativeCounts() {
        assertThatThrownBy(() -> store.save(new DistrictStats(2024, "15-134", 1, 1, Instant.now())))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> store.save(new DistrictStats(2024, "150135", -1, 1, Instant.now())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
