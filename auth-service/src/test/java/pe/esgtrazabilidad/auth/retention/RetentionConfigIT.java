package pe.esgtrazabilidad.auth.retention;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The migration seeds the decided defaults and keeps the table to one valid row. */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class RetentionConfigIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private RetentionConfigRepository repository;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void theDefaultsAreGpsNinetyDaysPhotosTwelveMonthsAndConsentVersionOne() {
        assertThat(repository.get()).isEqualTo(new RetentionConfig(90, 12, 1));
    }

    @Test
    void theTableCannotHoldASecondRowOrANonPositivePeriod() {
        assertThatThrownBy(() -> jdbc.update(
                        "INSERT INTO retention_config (id, gps_days, photo_months, consent_version, updated_at)"
                                + " VALUES (2, 90, 12, 1, now())"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE retention_config SET gps_days = 0 WHERE id = 1"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(repository.get()).isEqualTo(new RetentionConfig(90, 12, 1));
    }
}
