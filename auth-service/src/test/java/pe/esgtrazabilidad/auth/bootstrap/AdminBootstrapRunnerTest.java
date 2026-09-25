package pe.esgtrazabilidad.auth.bootstrap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

import pe.esgtrazabilidad.auth.staffuser.domain.StaffUser;
import pe.esgtrazabilidad.auth.staffuser.port.out.StaffUserRepository;

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

        verify(staffUserRepository, never()).existsAny();
        verify(staffUserRepository, never()).save(any());
    }

    @Test
    void doesNothingWhenTheTableAlreadyHasAStaffUser() {
        when(staffUserRepository.existsAny()).thenReturn(true);
        AdminBootstrapRunner runner =
                new AdminBootstrapRunner(staffUserRepository, passwordEncoder, "admin@esgtrazabilidad.pe");

        runner.run(new DefaultApplicationArguments());

        verify(staffUserRepository, never()).save(any());
    }

    @Test
    void createsExactlyOneAccountWithAHashedPasswordWhenEmailIsSetAndTableIsEmpty() {
        when(staffUserRepository.existsAny()).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("hashed-password");
        AdminBootstrapRunner runner =
                new AdminBootstrapRunner(staffUserRepository, passwordEncoder, "admin@esgtrazabilidad.pe");

        runner.run(new DefaultApplicationArguments());

        verify(staffUserRepository).save(any(StaffUser.class));
        verify(passwordEncoder).encode(any());
    }
}
