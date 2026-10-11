package pe.esgtrazabilidad.collection.neighbor.domain;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import pe.esgtrazabilidad.kernel.ubigeo.District;
import pe.esgtrazabilidad.kernel.ubigeo.UbigeoCatalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NeighborTest {

    private static final District SURCO = UbigeoCatalog.find("150140").orElseThrow();

    @Test
    void createsAnActiveNeighborWithAGeneratedIdAndTheCatalogDistrict() {
        Neighbor neighbor = Neighbor.create("Ana Torres", "999999999", "Av. Siempre Viva 123", SURCO);

        assertThat(neighbor.getId()).isNotNull();
        assertThat(neighbor.getStatus()).isEqualTo(NeighborStatus.ACTIVE);
        assertThat(neighbor.getFullName()).isEqualTo("Ana Torres");
        assertThat(neighbor.getPhone()).isEqualTo("999999999");
        assertThat(neighbor.getAddress()).isEqualTo("Av. Siempre Viva 123");
        assertThat(neighbor.getDistrictUbigeo()).isEqualTo("150140");
        assertThat(neighbor.getDistrict()).isEqualTo("Santiago de Surco");
    }

    @Test
    void rejectsANullAddress() {
        assertThatThrownBy(() -> Neighbor.create("Ana Torres", "999999999", null, SURCO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsABlankAddress() {
        assertThatThrownBy(() -> Neighbor.create("Ana Torres", "999999999", "   ", SURCO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsAMissingDistrict() {
        assertThatThrownBy(() -> Neighbor.create("Ana Torres", "999999999", "Av. Siempre Viva 123", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reconstructRebuildsAnExistingNeighborWithoutGeneratingANewId() {
        UUID id = UUID.randomUUID();

        Neighbor neighbor = Neighbor.reconstruct(
                id, "Ana Torres", "999999999", "Av. Siempre Viva 123", "Santiago de Surco", "150140",
                NeighborStatus.INACTIVE);

        assertThat(neighbor.getId()).isEqualTo(id);
        assertThat(neighbor.getStatus()).isEqualTo(NeighborStatus.INACTIVE);
    }

    @Test
    void showsTheCatalogNameForAKnownUbigeoAndTheStoredTextForAPreV3Row() {
        Neighbor backfilled = Neighbor.reconstruct(
                UUID.randomUUID(), "Ana", null, "Jr. 1", "rimac", "150128", NeighborStatus.ACTIVE);
        Neighbor unmatched = Neighbor.reconstruct(
                UUID.randomUUID(), "Ana", null, "Jr. 1", "Surco", null, NeighborStatus.ACTIVE);

        assertThat(backfilled.getDistrictDisplayName()).isEqualTo("Rímac");
        assertThat(unmatched.getDistrictDisplayName()).isEqualTo("Surco");
    }

    @Test
    void editReplacesTheContactDataAndTakesTheCatalogName() {
        Neighbor neighbor = Neighbor.create("Ana Torres", "999999999", "Av. Siempre Viva 123", SURCO);

        neighbor.edit("Ana María Torres", null, "Jr. Nuevo 45", UbigeoCatalog.find("150128").orElseThrow());

        assertThat(neighbor.getFullName()).isEqualTo("Ana María Torres");
        assertThat(neighbor.getPhone()).isNull();
        assertThat(neighbor.getAddress()).isEqualTo("Jr. Nuevo 45");
        assertThat(neighbor.getDistrictUbigeo()).isEqualTo("150128");
        assertThat(neighbor.getDistrict()).isEqualTo("Rímac");
        assertThat(neighbor.getStatus()).isEqualTo(NeighborStatus.ACTIVE);
    }

    @Test
    void editKeepsTheSameGuardsAsCreate() {
        Neighbor neighbor = Neighbor.create("Ana Torres", "999999999", "Av. Siempre Viva 123", SURCO);

        assertThatThrownBy(() -> neighbor.edit("Ana", null, " ", SURCO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> neighbor.edit("Ana", null, "Jr. 1", null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(neighbor.getAddress()).isEqualTo("Av. Siempre Viva 123");
    }

    @Test
    void deactivatesAnActiveNeighborAndReactivatesAnInactiveOne() {
        Neighbor neighbor = Neighbor.create("Ana Torres", "999999999", "Av. Siempre Viva 123", SURCO);

        neighbor.deactivate();
        assertThat(neighbor.getStatus()).isEqualTo(NeighborStatus.INACTIVE);
        assertThatThrownBy(neighbor::deactivate).isInstanceOf(IllegalStateException.class);

        neighbor.activate();
        assertThat(neighbor.getStatus()).isEqualTo(NeighborStatus.ACTIVE);
        assertThatThrownBy(neighbor::activate).isInstanceOf(IllegalStateException.class);
    }
}
