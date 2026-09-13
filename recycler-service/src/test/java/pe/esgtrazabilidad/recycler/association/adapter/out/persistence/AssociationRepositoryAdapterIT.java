package pe.esgtrazabilidad.recycler.association.adapter.out.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import pe.esgtrazabilidad.recycler.association.domain.Association;
import pe.esgtrazabilidad.recycler.association.domain.AssociationStatus;
import pe.esgtrazabilidad.recycler.association.port.out.AssociationRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class AssociationRepositoryAdapterIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private AssociationRepository associationRepository;

    private Logger sqlLogger;
    private ListAppender<ILoggingEvent> sqlAppender;

    @BeforeEach
    void captureHibernateSql() {
        sqlLogger = (Logger) LoggerFactory.getLogger("org.hibernate.SQL");
        sqlLogger.setLevel(Level.DEBUG);
        sqlAppender = new ListAppender<>();
        sqlAppender.start();
        sqlLogger.addAppender(sqlAppender);
    }

    @AfterEach
    void stopCapturingHibernateSql() {
        sqlLogger.detachAppender(sqlAppender);
    }

    @Test
    void savingANewAssociationIssuesOnlyOneInsertWithNoPriorSelect() {
        Association association = Association.create(
                "Asociación de prueba IT",
                "20123456789",
                "REG-IT-001",
                "Dirección de prueba",
                "it@test.pe",
                "999999999");

        associationRepository.save(association);

        List<String> statements = sqlAppender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .toList();

        assertThat(statements).hasSize(1);
        assertThat(statements.get(0)).startsWithIgnoringCase("insert");
    }

    @Test
    void savingASecondAssociationWithTheSameRucViolatesTheUniqueConstraint() {
        String sharedRuc = "20199999999";
        associationRepository.save(Association.create(
                "Primera asociación", sharedRuc, "REG-001", "Dirección", "a@b.pe", "999999999"));

        Association secondWithSameRuc = Association.create(
                "Segunda asociación", sharedRuc, "REG-002", "Otra dirección", "c@d.pe", "888888888");

        assertThatThrownBy(() -> associationRepository.save(secondWithSameRuc))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void updatingAnExistingAssociationPersistsTheChangeWithoutDuplicatingTheRow() {
        Association association = associationRepository.save(Association.create(
                "Asociación a actualizar", "20177777777", "REG-UPD-1", "Dirección", "a@b.pe", "999999999"));
        UUID id = association.getId();
        association.suspend();

        Association updated = associationRepository.update(association);

        assertThat(updated.getId()).isEqualTo(id);
        Optional<Association> reloaded = associationRepository.findById(id);
        assertThat(reloaded).isPresent();
        assertThat(reloaded.get().getStatus()).isEqualTo(AssociationStatus.SUSPENDED);
        // A wrongly-routed persist() of an id that already exists would throw
        // a duplicate-key violation rather than silently duplicating a row,
        // so reaching this assertion at all already proves update() went
        // through merge(), not persist() -- this just also checks the value stuck.
    }
}
