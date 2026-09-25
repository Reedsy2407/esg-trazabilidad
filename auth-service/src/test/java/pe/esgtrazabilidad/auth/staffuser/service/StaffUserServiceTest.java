package pe.esgtrazabilidad.auth.staffuser.service;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import pe.esgtrazabilidad.auth.staffuser.domain.StaffUser;
import pe.esgtrazabilidad.auth.staffuser.port.out.StaffUserRepository;
import pe.esgtrazabilidad.kernel.error.ApplicationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StaffUserServiceTest {

    @Mock
    private StaffUserRepository staffUserRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtIssuer jwtIssuer;

    private StaffUserService service;

    @BeforeEach
    void setUp() {
        service = new StaffUserService(staffUserRepository, passwordEncoder, jwtIssuer);
    }

    @Test
    void loginReturnsATokenWhenCredentialsAreValid() {
        StaffUser staffUser = StaffUser.create("ana@esgtrazabilidad.pe", "hashed-password", "Ana Pérez");
        when(staffUserRepository.findByEmail("ana@esgtrazabilidad.pe")).thenReturn(Optional.of(staffUser));
        when(passwordEncoder.matches("correct-password", "hashed-password")).thenReturn(true);
        when(jwtIssuer.issue(staffUser.getId(), staffUser.getEmail())).thenReturn("a-real-jwt");

        String token = service.login("ana@esgtrazabilidad.pe", "correct-password");

        assertThat(token).isEqualTo("a-real-jwt");
    }

    @Test
    void loginRejectsAnUnknownEmailWithInvalidCredentials() {
        when(staffUserRepository.findByEmail(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login("no-existe@esgtrazabilidad.pe", "whatever"))
                .isInstanceOf(ApplicationException.class)
                .hasMessage("Credenciales inválidas");
    }

    @Test
    void loginRejectsAWrongPasswordWithTheSameInvalidCredentialsError() {
        StaffUser staffUser = StaffUser.create("ana@esgtrazabilidad.pe", "hashed-password", "Ana Pérez");
        when(staffUserRepository.findByEmail("ana@esgtrazabilidad.pe")).thenReturn(Optional.of(staffUser));
        when(passwordEncoder.matches("wrong-password", "hashed-password")).thenReturn(false);

        assertThatThrownBy(() -> service.login("ana@esgtrazabilidad.pe", "wrong-password"))
                .isInstanceOf(ApplicationException.class)
                .hasMessage("Credenciales inválidas");
    }

    @Test
    void loginRejectsAnInactiveAccountEvenWithTheRightPassword() {
        StaffUser inactiveStaffUser = StaffUser.reconstruct(
                java.util.UUID.randomUUID(),
                "inactivo@esgtrazabilidad.pe",
                "hashed-password",
                "Inactivo",
                false,
                java.time.Instant.now());
        when(staffUserRepository.findByEmail("inactivo@esgtrazabilidad.pe")).thenReturn(Optional.of(inactiveStaffUser));

        assertThatThrownBy(() -> service.login("inactivo@esgtrazabilidad.pe", "any-password"))
                .isInstanceOf(ApplicationException.class)
                .hasMessage("Credenciales inválidas");
    }
}
