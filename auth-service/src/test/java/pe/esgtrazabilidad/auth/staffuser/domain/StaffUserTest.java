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
    void rejectsAnEmailWhoseDomainHasNoDot() {
        assertThat(StaffUser.isValidEmail("ana@esgtrazabilidad")).isFalse();
        assertThatThrownBy(() -> StaffUser.create("ana@esgtrazabilidad", "hashed-password", "Ana Pérez"))
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
    void aNewAccountIsStaffByDefaultAndAnAdminOnlyWhenAskedFor() {
        assertThat(StaffUser.create("ana@esgtrazabilidad.pe", "hash", "Ana").getRole()).isEqualTo(StaffRole.STAFF);
        StaffUser admin = StaffUser.create("jefa@esgtrazabilidad.pe", "hash", "Jefa", StaffRole.ADMIN, true);
        assertThat(admin.isAdmin()).isTrue();
        assertThat(admin.isMustChangePassword()).isTrue();
        assertThat(admin.getPasswordChangedAt()).isNull();
        assertThatThrownBy(() -> StaffUser.create("x@esgtrazabilidad.pe", "hash", "X", null, false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void withPasswordKeepsEverythingElseAndStampsTheChange() {
        StaffUser staffUser = StaffUser.create("ana@esgtrazabilidad.pe", "old", "Ana", StaffRole.ADMIN, true);
        Instant now = Instant.parse("2026-10-15T20:00:00Z");

        StaffUser changed = staffUser.withPassword("new", false, now);

        assertThat(changed.getPasswordHash()).isEqualTo("new");
        assertThat(changed.isMustChangePassword()).isFalse();
        assertThat(changed.getPasswordChangedAt()).isEqualTo(now);
        assertThat(changed.getId()).isEqualTo(staffUser.getId());
        assertThat(changed.getRole()).isEqualTo(StaffRole.ADMIN);
        assertThat(staffUser.getPasswordHash()).isEqualTo("old"); // immutable
        assertThatThrownBy(() -> staffUser.withPassword(" ", false, now)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deactivateAndReactivateKeepTheLastStatusChange() {
        UUID admin = UUID.randomUUID();
        Instant now = Instant.parse("2026-10-15T20:00:00Z");
        StaffUser staffUser = StaffUser.create("ana@esgtrazabilidad.pe", "hash", "Ana");

        StaffUser off = staffUser.deactivate("se fue", admin, now);
        assertThat(off.isActive()).isFalse();
        assertThat(off.getStatusReason()).isEqualTo("se fue");
        assertThat(off.getStatusChangedBy()).isEqualTo(admin);
        assertThat(off.getStatusChangedAt()).isEqualTo(now);

        StaffUser on = off.reactivate("volvió", admin, now.plusSeconds(60));
        assertThat(on.isActive()).isTrue();
        assertThat(on.getStatusReason()).isEqualTo("volvió");
        assertThat(on.getStatusChangedAt()).isEqualTo(now.plusSeconds(60));
    }

    @Test
    void promoteToAdminChangesOnlyTheRole() {
        StaffUser staffUser = StaffUser.create("ana@esgtrazabilidad.pe", "hash", "Ana");

        StaffUser admin = staffUser.promoteToAdmin();

        assertThat(admin.getRole()).isEqualTo(StaffRole.ADMIN);
        assertThat(admin.getId()).isEqualTo(staffUser.getId());
        assertThat(admin.getPasswordHash()).isEqualTo("hash");
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
