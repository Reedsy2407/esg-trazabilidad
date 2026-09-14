package pe.esgtrazabilidad.collection.neighbor.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NeighborTest {

    @Test
    void createsAnActiveNeighborWithAGeneratedId() {
        Neighbor neighbor = Neighbor.create("Ana Torres", "999999999", "Av. Siempre Viva 123", "Surco");

        assertThat(neighbor.getId()).isNotNull();
        assertThat(neighbor.getStatus()).isEqualTo(NeighborStatus.ACTIVE);
        assertThat(neighbor.getFullName()).isEqualTo("Ana Torres");
        assertThat(neighbor.getPhone()).isEqualTo("999999999");
        assertThat(neighbor.getAddress()).isEqualTo("Av. Siempre Viva 123");
        assertThat(neighbor.getDistrict()).isEqualTo("Surco");
    }

    @Test
    void reconstructRebuildsAnExistingNeighborWithoutGeneratingANewId() {
        java.util.UUID id = java.util.UUID.randomUUID();

        Neighbor neighbor = Neighbor.reconstruct(
                id, "Ana Torres", "999999999", "Av. Siempre Viva 123", "Surco", NeighborStatus.INACTIVE);

        assertThat(neighbor.getId()).isEqualTo(id);
        assertThat(neighbor.getStatus()).isEqualTo(NeighborStatus.INACTIVE);
    }
}
