package pe.esgtrazabilidad.auth.staffuser.domain;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StaffUserTest {

    @Test
    void createsAnActiveStaffUserWithAGeneratedId() {
        StaffUser staffUser = StaffUser.create("ana@esgtrazabilidad.pe", "hashed-password", "Ana Pérez");

        assertThat(staffUser.getId()).isNotNull();
        assertThat(staffUser.getEmail()).isEqualTo("ana@esgtrazabilidad.pe");
        assertThat(staffUser.getPasswordHash()).isEqualTo("hashed-password");
        assertThat(staffUser.getFullName()).isEqualTo("Ana Pérez");
        assertThat(staffUser.isActive()).isTrue();
        assertThat(staffUser.getCreatedAt()).isNotNull();
    }

    @Test
    void lowercasesTheEmailOnCreation() {
        StaffUser staffUser = StaffUser.create("Ana@ESGtrazabilidad.pe", "hashed-password", "Ana Pérez");

        assertThat(staffUser.getEmail()).isEqualTo("ana@esgtrazabilidad.pe");
    }

    @Test
    void rejectsAnEmailWithNoAtSign() {
        assertThatThrownBy(() -> StaffUser.create("not-an-email", "hashed-password", "Ana Pérez"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsABlankPasswordHash() {
        assertThatThrownBy(() -> StaffUser.create("ana@esgtrazabilidad.pe", " ", "Ana Pérez"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsABlankFullName() {
        assertThatThrownBy(() -> StaffUser.create("ana@esgtrazabilidad.pe", "hashed-password", " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reconstructRebuildsAnExistingStaffUserWithoutGeneratingANewId() {
        UUID id = UUID.randomUUID();
        Instant createdAt = Instant.parse("2026-01-01T00:00:00Z");

        StaffUser staffUser =
                StaffUser.reconstruct(id, "ana@esgtrazabilidad.pe", "hashed-password", "Ana Pérez", false, createdAt);

        assertThat(staffUser.getId()).isEqualTo(id);
        assertThat(staffUser.isActive()).isFalse();
        assertThat(staffUser.getCreatedAt()).isEqualTo(createdAt);
    }
}
