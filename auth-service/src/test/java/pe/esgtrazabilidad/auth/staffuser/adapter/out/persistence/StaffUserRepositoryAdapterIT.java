package pe.esgtrazabilidad.auth.staffuser.adapter.out.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import pe.esgtrazabilidad.auth.staffuser.domain.StaffUser;
import pe.esgtrazabilidad.auth.staffuser.port.out.StaffUserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Proves the DB-level unique constraint on email fires even bypassing any
 * service-level check -- same discipline as recycler-service's
 * AssociationRepositoryAdapterIT.savingASecondAssociationWithTheSameRucViolatesTheUniqueConstraint.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class StaffUserRepositoryAdapterIT {

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

    // Package-private, but this test lives in the same package -- used only
    // to reset table state between tests. @SpringBootTest shares one context
    // (and one Testcontainers Postgres) across every test method in this
    // class, so existsAnyIsFalseOnAnEmptyTableAndTrueOnceARowExists's "empty
    // table" premise would otherwise depend on unspecified test execution
    // order against rows the other tests already inserted.
    @Autowired
    private StaffUserJpaRepository jpaRepository;

    @BeforeEach
    void cleanTable() {
        jpaRepository.deleteAll();
    }

    @Test
    void savingAStaffUserPersistsItWithAGeneratedId() {
        StaffUser staffUser = staffUserRepository.save(
                StaffUser.create("ana@esgtrazabilidad.pe", "hashed-password", "Ana Pérez"));

        assertThat(staffUserRepository.findById(staffUser.getId())).isPresent();
    }

    @Test
    void savingASecondStaffUserWithTheSameEmailViolatesTheUniqueConstraint() {
        String sharedEmail = "duplicada@esgtrazabilidad.pe";
        staffUserRepository.save(StaffUser.create(sharedEmail, "hashed-password", "Primera"));

        StaffUser secondWithSameEmail = StaffUser.create(sharedEmail, "hashed-password", "Segunda");

        assertThatThrownBy(() -> staffUserRepository.save(secondWithSameEmail))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findByEmailFindsAPersistedStaffUser() {
        staffUserRepository.save(StaffUser.create("buscar@esgtrazabilidad.pe", "hashed-password", "Buscar"));

        assertThat(staffUserRepository.findByEmail("buscar@esgtrazabilidad.pe")).isPresent();
        assertThat(staffUserRepository.findByEmail("no-existe@esgtrazabilidad.pe")).isEmpty();
    }

    @Test
    void existsAnyIsFalseOnAnEmptyTableAndTrueOnceARowExists() {
        assertThat(staffUserRepository.existsAny()).isFalse();

        staffUserRepository.save(StaffUser.create("primero@esgtrazabilidad.pe", "hashed-password", "Primero"));

        assertThat(staffUserRepository.existsAny()).isTrue();
    }
}
