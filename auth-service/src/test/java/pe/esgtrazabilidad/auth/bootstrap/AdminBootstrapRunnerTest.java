package pe.esgtrazabilidad.auth.bootstrap;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

import pe.esgtrazabilidad.auth.staffuser.domain.StaffRole;
import pe.esgtrazabilidad.auth.staffuser.domain.StaffUser;
import pe.esgtrazabilidad.auth.staffuser.port.out.StaffUserRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapRunnerTest {

    @Mock
    private StaffUserRepository staffUserRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Test
    void doesNothingWhenNoBootstrapEmailIsConfigured() {
        AdminBootstrapRunner runner = new AdminBootstrapRunner(staffUserRepository, passwordEncoder, "");

        runner.run(new DefaultApplicationArguments());

        verify(staffUserRepository, never()).save(any());
    }

    @Test
    void doesNothingWhenAnActiveAdminAlreadyExists() {
        when(staffUserRepository.existsAny()).thenReturn(true);
        when(staffUserRepository.existsActiveAdmin()).thenReturn(true);
        AdminBootstrapRunner runner =
                new AdminBootstrapRunner(staffUserRepository, passwordEncoder, "admin@esgtrazabilidad.pe");

        runner.run(new DefaultApplicationArguments());

        verify(staffUserRepository, never()).save(any());
    }

    @Test
    void createsExactlyOneAdminWithAHashedPasswordThatMustBeChangedWhenEmailIsSetAndTableIsEmpty() {
        when(staffUserRepository.existsAny()).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hashed-password");
        AdminBootstrapRunner runner =
                new AdminBootstrapRunner(staffUserRepository, passwordEncoder, "admin@esgtrazabilidad.pe");

        runner.run(new DefaultApplicationArguments());

        ArgumentCaptor<StaffUser> saved = ArgumentCaptor.forClass(StaffUser.class);
        verify(staffUserRepository).save(saved.capture());
        assertThat(saved.getValue().getRole()).isEqualTo(StaffRole.ADMIN);
        assertThat(saved.getValue().isMustChangePassword()).isTrue();
        verify(passwordEncoder).encode(any());
    }

    @Test
    void promotesTheNamedActiveAccountWhenAccountsExistButNoneIsAnActiveAdmin() {
        StaffUser existing = StaffUser.create("jefa@esgtrazabilidad.pe", "hash", "Jefa");
        when(staffUserRepository.existsAny()).thenReturn(true);
        when(staffUserRepository.existsActiveAdmin()).thenReturn(false);
        when(staffUserRepository.findByEmail("jefa@esgtrazabilidad.pe")).thenReturn(Optional.of(existing));
        AdminBootstrapRunner runner =
                new AdminBootstrapRunner(staffUserRepository, passwordEncoder, "Jefa@esgtrazabilidad.pe");

        runner.run(new DefaultApplicationArguments());

        ArgumentCaptor<StaffUser> saved = ArgumentCaptor.forClass(StaffUser.class);
        verify(staffUserRepository).save(saved.capture());
        assertThat(saved.getValue().getId()).isEqualTo(existing.getId());
        assertThat(saved.getValue().getRole()).isEqualTo(StaffRole.ADMIN);
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("hash"); // credentials untouched
    }

    @Test
    void doesNotPromoteAnInactiveOrUnknownAccount() {
        StaffUser inactive = StaffUser.create("baja@esgtrazabilidad.pe", "hash", "Baja")
                .deactivate("baja", java.util.UUID.randomUUID(), java.time.Instant.now());
        when(staffUserRepository.existsAny()).thenReturn(true);
        when(staffUserRepository.existsActiveAdmin()).thenReturn(false);
        when(staffUserRepository.findByEmail("baja@esgtrazabilidad.pe")).thenReturn(Optional.of(inactive));
        when(staffUserRepository.findByEmail("nadie@esgtrazabilidad.pe")).thenReturn(Optional.empty());

        new AdminBootstrapRunner(staffUserRepository, passwordEncoder, "baja@esgtrazabilidad.pe")
                .run(new DefaultApplicationArguments());
        new AdminBootstrapRunner(staffUserRepository, passwordEncoder, "nadie@esgtrazabilidad.pe")
                .run(new DefaultApplicationArguments());

        verify(staffUserRepository, never()).save(any());
    }
}
