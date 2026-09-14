package pe.esgtrazabilidad.collection.collectionrecord.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CollectionRecordTest {

    private final UUID neighborId = UUID.randomUUID();
    private final UUID associationId = UUID.randomUUID();

    @Test
    void createsARecordWithAGeneratedId() {
        CollectionRecord record = CollectionRecord.create(
                neighborId, null, associationId, LocalDate.now(), new BigDecimal("12.5"));

        assertThat(record.getId()).isNotNull();
        assertThat(record.getNeighborId()).isEqualTo(neighborId);
        assertThat(record.getScheduleId()).isNull();
        assertThat(record.getAssociationId()).isEqualTo(associationId);
        assertThat(record.getCollectionDate()).isEqualTo(LocalDate.now());
        assertThat(record.getWeightKg()).isEqualByComparingTo("12.5");
    }

    @Test
    void createsARecordLinkedToAScheduleWhenOneIsGiven() {
        UUID scheduleId = UUID.randomUUID();

        CollectionRecord record = CollectionRecord.create(
                neighborId, scheduleId, associationId, LocalDate.now(), new BigDecimal("5"));

        assertThat(record.getScheduleId()).isEqualTo(scheduleId);
    }

    @Test
    void rejectsAZeroWeight() {
        assertThatThrownBy(() -> CollectionRecord.create(
                        neighborId, null, associationId, LocalDate.now(), BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsANegativeWeight() {
        assertThatThrownBy(() -> CollectionRecord.create(
                        neighborId, null, associationId, LocalDate.now(), new BigDecimal("-1")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsANullCollectionDate() {
        assertThatThrownBy(() -> CollectionRecord.create(
                        neighborId, null, associationId, null, new BigDecimal("5")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reconstructRebuildsAnExistingRecordWithoutGeneratingANewId() {
        UUID id = UUID.randomUUID();

        CollectionRecord record = CollectionRecord.reconstruct(
                id, neighborId, null, associationId, LocalDate.now(), new BigDecimal("7"));

        assertThat(record.getId()).isEqualTo(id);
    }
}
