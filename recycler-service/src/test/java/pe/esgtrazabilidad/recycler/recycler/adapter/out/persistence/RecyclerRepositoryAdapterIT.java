package pe.esgtrazabilidad.recycler.recycler.adapter.out.persistence;

import java.util.UUID;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import pe.esgtrazabilidad.recycler.association.domain.Association;
import pe.esgtrazabilidad.recycler.association.port.out.AssociationRepository;
import pe.esgtrazabilidad.recycler.recycler.domain.Recycler;
import pe.esgtrazabilidad.recycler.recycler.port.out.RecyclerRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class RecyclerRepositoryAdapterIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private RecyclerRepository recyclerRepository;

    @Autowired
    private AssociationRepository associationRepository;

    private UUID persistAssociation() {
        Association association = Association.create(
                "Asociación IT", "20155555555", "REG-IT", "Dirección", "it@test.pe", "999999999");
        return associationRepository.save(association).getId();
    }

    @Test
    void savingASecondRecyclerWithTheSameDniViolatesTheUniqueConstraint() {
        UUID associationId = persistAssociation();
        String sharedDni = "12399999";
        recyclerRepository.save(Recycler.create("Primer reciclador", sharedDni, "999999999", associationId));

        Recycler secondWithSameDni = Recycler.create("Segundo reciclador", sharedDni, "888888888", associationId);

        assertThatThrownBy(() -> recyclerRepository.save(secondWithSameDni))
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(exception -> {
                    Throwable cause = exception.getCause();
                    assertThat(cause).isInstanceOf(ConstraintViolationException.class);
                    assertThat(((ConstraintViolationException) cause).getConstraintName())
                            .isEqualTo("recycler_dni_key");
                });
    }

    @Test
    void savingARecyclerWithANonexistentAssociationViolatesTheForeignKeyConstraint() {
        UUID missingAssociationId = UUID.randomUUID();
        Recycler recycler = Recycler.create("Reciclador huérfano", "87654321", "999999999", missingAssociationId);

        assertThatThrownBy(() -> recyclerRepository.save(recycler))
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(exception -> {
                    Throwable cause = exception.getCause();
                    assertThat(cause).isInstanceOf(ConstraintViolationException.class);
                    assertThat(((ConstraintViolationException) cause).getConstraintName())
                            .isEqualTo("fk_recycler_association");
                });
    }
}
