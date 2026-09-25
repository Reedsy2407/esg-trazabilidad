package pe.esgtrazabilidad.auth.bootstrap;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import pe.esgtrazabilidad.auth.staffuser.domain.StaffUser;
import pe.esgtrazabilidad.auth.staffuser.port.out.StaffUserRepository;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ADMIN_BOOTSTRAP_EMAIL is deliberately left unset for this test class's own
 * Spring context (no @DynamicPropertySource entry for it), so the
 * auto-wired AdminBootstrapRunner bean does nothing during context startup
 * -- every test method here constructs its own AdminBootstrapRunner
 * directly against the real, autowired StaffUserRepository/PasswordEncoder
 * beans and calls run() explicitly, for full control over log-capture
 * timing and to simulate a real second-startup call without needing a
 * second full Spring context.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class AdminBootstrapIT {

    private static final Pattern PASSWORD_PATTERN = Pattern.compile("password=(\\S+)");

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private StaffUserRepository staffUserRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // existsAny() is deliberately table-wide (bootstrap only cares "does ANY
    // staff account exist", never per-email), so this shared-context test
    // class's two methods would otherwise interfere with each other
    // depending on unspecified JUnit method execution order -- whichever
    // runs second would see existsAny()==true from the first one's leftover
    // row and skip its own bootstrap, exactly the real bug this caught the
    // first time this test was run without this cleanup.
    @BeforeEach
    void cleanTable() {
        jdbcTemplate.update("DELETE FROM staff_user");
    }

    @Test
    void firstBootstrapCreatesExactlyOneAccountWithARealLoggedPasswordThatActuallyWorks() {
        String email = "primer-arranque@esgtrazabilidad.pe";
        AdminBootstrapRunner runner = new AdminBootstrapRunner(staffUserRepository, passwordEncoder, email);

        List<ILoggingEvent> events = captureLogsDuring(() -> runner.run(new DefaultApplicationArguments()));

        assertThat(staffUserRepository.findByEmail(email)).isPresent();
        StaffUser created = staffUserRepository.findByEmail(email).orElseThrow();

        assertThat(events).hasSize(1);
        assertThat(events.get(0).getLevel()).isEqualTo(Level.WARN);
        String loggedPassword = extractPassword(events.get(0).getFormattedMessage());
        assertThat(passwordEncoder.matches(loggedPassword, created.getPasswordHash())).isTrue();
    }

    @Test
    void aSecondBootstrapCallWithTheSameEmailNeverCreatesASecondAccountOrChangesTheExistingHash() {
        String email = "segundo-arranque@esgtrazabilidad.pe";
        AdminBootstrapRunner firstRunner = new AdminBootstrapRunner(staffUserRepository, passwordEncoder, email);
        firstRunner.run(new DefaultApplicationArguments());
        String hashAfterFirstBoot =
                staffUserRepository.findByEmail(email).orElseThrow().getPasswordHash();

        // A fresh runner instance, same as a real process restart would construct --
        // proves the no-op is driven by real table state, not any in-memory flag.
        AdminBootstrapRunner secondRunner = new AdminBootstrapRunner(staffUserRepository, passwordEncoder, email);
        List<ILoggingEvent> eventsOnSecondCall =
                captureLogsDuring(() -> secondRunner.run(new DefaultApplicationArguments()));

        assertThat(eventsOnSecondCall).isEmpty();
        StaffUser afterSecondCall = staffUserRepository.findByEmail(email).orElseThrow();
        assertThat(afterSecondCall.getPasswordHash()).isEqualTo(hashAfterFirstBoot);
    }

    private String extractPassword(String logMessage) {
        Matcher matcher = PASSWORD_PATTERN.matcher(logMessage);
        assertThat(matcher.find()).as("logged bootstrap message should contain password=<value>").isTrue();
        return matcher.group(1);
    }

    private List<ILoggingEvent> captureLogsDuring(Runnable action) {
        Logger logger = (Logger) LoggerFactory.getLogger(AdminBootstrapRunner.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            action.run();
        } finally {
            logger.detachAppender(appender);
        }
        return appender.list;
    }
}
