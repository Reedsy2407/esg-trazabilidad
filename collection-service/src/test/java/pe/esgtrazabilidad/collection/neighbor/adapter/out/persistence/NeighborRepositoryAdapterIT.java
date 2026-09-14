package pe.esgtrazabilidad.collection.neighbor.adapter.out.persistence;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import pe.esgtrazabilidad.collection.neighbor.domain.Neighbor;
import pe.esgtrazabilidad.collection.neighbor.domain.NeighborStatus;
import pe.esgtrazabilidad.collection.neighbor.port.out.NeighborRepository;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class NeighborRepositoryAdapterIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private NeighborRepository neighborRepository;

    @Test
    void savingANeighborWithoutAnAddressViolatesTheDatabaseConstraint() {
        // Neighbor.reconstruct() bypasses the domain guard in create() on
        // purpose, so this proves the NOT NULL constraint itself fires at
        // the database level -- not just that the domain check works.
        Neighbor neighborWithoutAddress = Neighbor.reconstruct(
                UUID.randomUUID(), "Ana Torres", "999999999", null, "Surco", NeighborStatus.ACTIVE);

        assertThatThrownBy(() -> neighborRepository.save(neighborWithoutAddress))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
