package pe.esgtrazabilidad.auth.staffuser.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import pe.esgtrazabilidad.auth.exception.AuthErrors;
import pe.esgtrazabilidad.auth.staffuser.domain.StaffRole;
import pe.esgtrazabilidad.auth.staffuser.domain.StaffUser;
import pe.esgtrazabilidad.auth.staffuser.port.in.CreateStaffUserCommand;
import pe.esgtrazabilidad.auth.staffuser.port.out.StaffUserRepository;
import pe.esgtrazabilidad.kernel.error.ApplicationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StaffUserServiceTest {

    private static final String STRONG = "una-clave-larga-y-segura";

    @Mock
    private StaffUserRepository staffUserRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtIssuer jwtIssuer;

    private StaffUserService service;
    private StaffUser admin;

    @BeforeEach
    void setUp() {
        service = new StaffUserService(staffUserRepository, passwordEncoder, jwtIssuer);
        admin = StaffUser.create("admin@esgtrazabilidad.pe", "hashed-admin", "Admin", StaffRole.ADMIN, false);
    }

    private void adminSignedIn() {
        when(staffUserRepository.findById(admin.getId())).thenReturn(Optional.of(admin));
    }

    private static void assertError(Throwable thrown, AuthErrors expected) {
        assertThat(thrown).isInstanceOf(ApplicationException.class);
        assertThat(((ApplicationException) thrown).getError()).isEqualTo(expected);
    }

    // --- login ---------------------------------------------------------------------------------

    @Test
    void loginReturnsATokenWithTheRoleAndThePendingChangeFlag() {
        StaffUser staffUser = StaffUser.create("ana@esgtrazabilidad.pe", "hashed-password", "Ana Pérez", StaffRole.STAFF, true);
        when(staffUserRepository.findByEmail("ana@esgtrazabilidad.pe")).thenReturn(Optional.of(staffUser));
        when(passwordEncoder.matches("correct-password", "hashed-password")).thenReturn(true);
        when(jwtIssuer.issue(staffUser.getId(), staffUser.getEmail(), StaffRole.STAFF, true)).thenReturn("a-real-jwt");

        assertThat(service.login("ana@esgtrazabilidad.pe", "correct-password")).isEqualTo("a-real-jwt");
    }

    @Test
    void loginLowercasesTheEmailBeforeLookingItUp() {
        StaffUser staffUser = StaffUser.create("ana@esgtrazabilidad.pe", "hashed-password", "Ana Pérez");
        when(staffUserRepository.findByEmail("ana@esgtrazabilidad.pe")).thenReturn(Optional.of(staffUser));
        when(passwordEncoder.matches("correct-password", "hashed-password")).thenReturn(true);
        when(jwtIssuer.issue(staffUser.getId(), staffUser.getEmail(), StaffRole.STAFF, false)).thenReturn("a-real-jwt");

        assertThat(service.login("Ana@ESGtrazabilidad.PE", "correct-password")).isEqualTo("a-real-jwt");
    }

    @Test
    void loginComparesTheNormalisedPassword() {
        StaffUser staffUser = StaffUser.create("ana@esgtrazabilidad.pe", "hashed-password", "Ana Pérez");
        when(staffUserRepository.findByEmail("ana@esgtrazabilidad.pe")).thenReturn(Optional.of(staffUser));
        when(passwordEncoder.matches("café", "hashed-password")).thenReturn(true);
        when(jwtIssuer.issue(any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean())).thenReturn("jwt");

        assertThat(service.login("ana@esgtrazabilidad.pe", "café")).isEqualTo("jwt");
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
                UUID.randomUUID(), "inactivo@esgtrazabilidad.pe", "hashed-password", "Inactivo", false, Instant.now());
        when(staffUserRepository.findByEmail("inactivo@esgtrazabilidad.pe")).thenReturn(Optional.of(inactiveStaffUser));

        assertThatThrownBy(() -> service.login("inactivo@esgtrazabilidad.pe", "any-password"))
                .isInstanceOf(ApplicationException.class)
                .hasMessage("Credenciales inválidas");
    }

    // --- create ----------------------------------------------------------------------------------

    @Test
    void createStaffUserHashesThePasswordAndSavesAStaffAccountThatMustChangeIt() {
        adminSignedIn();
        when(staffUserRepository.findByEmail("nueva@esgtrazabilidad.pe")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(STRONG)).thenReturn("hashed-password");
        when(staffUserRepository.save(any(StaffUser.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StaffUser created = service.createStaffUser(
                new CreateStaffUserCommand(admin.getId(), "nueva@esgtrazabilidad.pe", STRONG, "Nueva Persona", null));

        assertThat(created.getEmail()).isEqualTo("nueva@esgtrazabilidad.pe");
        assertThat(created.getPasswordHash()).isEqualTo("hashed-password");
        assertThat(created.getRole()).isEqualTo(StaffRole.STAFF);
        assertThat(created.isMustChangePassword()).isTrue();
        assertThat(created.getPasswordChangedAt()).isNull();
    }

    @Test
    void createStaffUserCanMakeAnotherAdmin() {
        adminSignedIn();
        when(staffUserRepository.findByEmail(any())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(any())).thenReturn("hashed");
        when(staffUserRepository.save(any(StaffUser.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StaffUser created = service.createStaffUser(new CreateStaffUserCommand(
                admin.getId(), "otra@esgtrazabilidad.pe", STRONG, "Otra Admin", StaffRole.ADMIN));

        assertThat(created.getRole()).isEqualTo(StaffRole.ADMIN);
    }

    @Test
    void aStaffMemberCannotCreateAccounts() {
        StaffUser staff = StaffUser.create("staff@esgtrazabilidad.pe", "hashed", "Staff");
        when(staffUserRepository.findById(staff.getId())).thenReturn(Optional.of(staff));

        assertThatThrownBy(() -> service.createStaffUser(
                        new CreateStaffUserCommand(staff.getId(), "nueva@esgtrazabilidad.pe", STRONG, "Nueva", null)))
                .satisfies(thrown -> assertError(thrown, AuthErrors.ADMIN_REQUIRED));
        verify(staffUserRepository, never()).save(any());
    }

    @Test
    void aDeactivatedAdminCannotCreateAccountsEvenWithAStillValidToken() {
        StaffUser deactivated = admin.deactivate("baja", UUID.randomUUID(), Instant.now());
        when(staffUserRepository.findById(admin.getId())).thenReturn(Optional.of(deactivated));

        assertThatThrownBy(() -> service.createStaffUser(
                        new CreateStaffUserCommand(admin.getId(), "nueva@esgtrazabilidad.pe", STRONG, "Nueva", null)))
                .satisfies(thrown -> assertError(thrown, AuthErrors.ADMIN_REQUIRED));
    }

    @Test
    void createStaffUserRejectsAnEmailWithNoDotInTheDomainBeforeHashingOrSaving() {
        adminSignedIn();

        assertThatThrownBy(() -> service.createStaffUser(
                        new CreateStaffUserCommand(admin.getId(), "nueva@esgtrazabilidad", STRONG, "Nueva Persona", null)))
                .satisfies(thrown -> assertError(thrown, AuthErrors.INVALID_STAFF_EMAIL));
        verify(passwordEncoder, never()).encode(any());
        verify(staffUserRepository, never()).save(any());
    }

    @Test
    void createStaffUserRejectsAWeakPasswordBeforeHashingOrSaving() {
        adminSignedIn();

        assertThatThrownBy(() -> service.createStaffUser(
                        new CreateStaffUserCommand(admin.getId(), "nueva@esgtrazabilidad.pe", "corta", "Nueva", null)))
                .satisfies(thrown -> assertError(thrown, AuthErrors.WEAK_PASSWORD));
        verify(passwordEncoder, never()).encode(any());
        verify(staffUserRepository, never()).save(any());
    }

    @Test
    void createStaffUserRejectsADuplicateEmailWithoutSaving() {
        adminSignedIn();
        StaffUser existing = StaffUser.create("duplicada@esgtrazabilidad.pe", "hashed-password", "Ya existe");
        when(staffUserRepository.findByEmail("duplicada@esgtrazabilidad.pe")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.createStaffUser(new CreateStaffUserCommand(
                        admin.getId(), "duplicada@esgtrazabilidad.pe", STRONG, "Otra Persona", null)))
                .isInstanceOf(ApplicationException.class)
                .hasMessage("Ya existe una cuenta de staff con ese email");
        verify(staffUserRepository, never()).save(any());
    }

    // --- current / list --------------------------------------------------------------------------

    @Test
    void getCurrentReturnsTheMatchingStaffUser() {
        StaffUser staffUser = StaffUser.create("actual@esgtrazabilidad.pe", "hashed-password", "Usuario Actual");
        when(staffUserRepository.findById(staffUser.getId())).thenReturn(Optional.of(staffUser));

        assertThat(service.getCurrent(staffUser.getId())).isEqualTo(staffUser);
    }

    @Test
    void getCurrentThrowsStaffUserNotFoundForAnUnknownId() {
        UUID unknownId = UUID.randomUUID();
        when(staffUserRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCurrent(unknownId))
                .isInstanceOf(ApplicationException.class)
                .hasMessage("Cuenta de staff no encontrada");
    }

    @Test
    void listIsForAdminsOnly() {
        StaffUser staff = StaffUser.create("staff@esgtrazabilidad.pe", "hashed", "Staff");
        when(staffUserRepository.findById(staff.getId())).thenReturn(Optional.of(staff));

        assertThatThrownBy(() -> service.list(staff.getId())).satisfies(thrown -> assertError(thrown, AuthErrors.ADMIN_REQUIRED));
        verify(staffUserRepository, never()).findAll();
    }

    @Test
    void listReturnsEveryAccountToAnAdmin() {
        adminSignedIn();
        when(staffUserRepository.findAll()).thenReturn(List.of(admin));

        assertThat(service.list(admin.getId())).containsExactly(admin);
    }

    // --- deactivate / reactivate -----------------------------------------------------------------

    @Test
    void deactivateKeepsTheReasonWhoAndWhen() {
        adminSignedIn();
        StaffUser target = StaffUser.create("ana@esgtrazabilidad.pe", "hashed", "Ana");
        when(staffUserRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(staffUserRepository.save(any(StaffUser.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StaffUser saved = service.deactivate(admin.getId(), target.getId(), "  Dejó la asociación  ");

        assertThat(saved.isActive()).isFalse();
        assertThat(saved.getStatusReason()).isEqualTo("Dejó la asociación");
        assertThat(saved.getStatusChangedBy()).isEqualTo(admin.getId());
        assertThat(saved.getStatusChangedAt()).isNotNull();
    }

    @Test
    void anAdminCannotDeactivateTheirOwnAccountSoTheSystemNeverLosesItsLastAdmin() {
        adminSignedIn();

        assertThatThrownBy(() -> service.deactivate(admin.getId(), admin.getId(), "me voy"))
                .satisfies(thrown -> assertError(thrown, AuthErrors.CANNOT_ACT_ON_OWN_ACCOUNT));
        verify(staffUserRepository, never()).save(any());
    }

    @Test
    void deactivatingAnInactiveAccountIsRefused() {
        adminSignedIn();
        StaffUser inactive = StaffUser.create("ana@esgtrazabilidad.pe", "hashed", "Ana").deactivate("antes", admin.getId(), Instant.now());
        when(staffUserRepository.findById(inactive.getId())).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> service.deactivate(admin.getId(), inactive.getId(), "otra vez"))
                .satisfies(thrown -> assertError(thrown, AuthErrors.STATUS_UNCHANGED));
    }

    @Test
    void reactivateTurnsAnInactiveAccountBackOn() {
        adminSignedIn();
        StaffUser inactive = StaffUser.create("ana@esgtrazabilidad.pe", "hashed", "Ana").deactivate("antes", admin.getId(), Instant.now());
        when(staffUserRepository.findById(inactive.getId())).thenReturn(Optional.of(inactive));
        when(staffUserRepository.save(any(StaffUser.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StaffUser saved = service.reactivate(admin.getId(), inactive.getId(), "volvió");

        assertThat(saved.isActive()).isTrue();
        assertThat(saved.getStatusReason()).isEqualTo("volvió");
    }

    @Test
    void reactivatingAnActiveAccountIsRefused() {
        adminSignedIn();
        StaffUser active = StaffUser.create("ana@esgtrazabilidad.pe", "hashed", "Ana");
        when(staffUserRepository.findById(active.getId())).thenReturn(Optional.of(active));

        assertThatThrownBy(() -> service.reactivate(admin.getId(), active.getId(), "ya estaba"))
                .satisfies(thrown -> assertError(thrown, AuthErrors.STATUS_UNCHANGED));
    }

    @Test
    void deactivatingAnUnknownAccountIsNotFound() {
        adminSignedIn();
        UUID unknown = UUID.randomUUID();
        when(staffUserRepository.findById(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deactivate(admin.getId(), unknown, "x"))
                .satisfies(thrown -> assertError(thrown, AuthErrors.STAFF_USER_NOT_FOUND));
    }

    // --- passwords -------------------------------------------------------------------------------

    @Test
    void anAdminResetMarksThePasswordAsOneThePersonMustChange() {
        adminSignedIn();
        StaffUser target = StaffUser.create("ana@esgtrazabilidad.pe", "old-hash", "Ana");
        when(staffUserRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(passwordEncoder.encode(STRONG)).thenReturn("new-hash");

        service.resetPassword(admin.getId(), target.getId(), STRONG);

        // Only the password columns are written, never the whole row (a concurrent deactivation survives).
        verify(staffUserRepository).updatePassword(eq(target.getId()), eq("new-hash"), eq(true), any(Instant.class));
        verify(staffUserRepository, never()).save(any());
    }

    @Test
    void deactivatingLocksTheActiveAdminsFirstSoTwoAdminsCannotDeactivateEachOther() {
        adminSignedIn();
        StaffUser target = StaffUser.create("ana@esgtrazabilidad.pe", "hashed", "Ana");
        when(staffUserRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(staffUserRepository.save(any(StaffUser.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.deactivate(admin.getId(), target.getId(), "baja");

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(staffUserRepository);
        order.verify(staffUserRepository).lockActiveAdmins();
        order.verify(staffUserRepository).findById(admin.getId());
    }

    @Test
    void anOldPasswordStoredWithoutNormalisationStillLogsIn() {
        StaffUser staffUser = StaffUser.create("ana@esgtrazabilidad.pe", "hashed-password", "Ana Pérez");
        when(staffUserRepository.findByEmail("ana@esgtrazabilidad.pe")).thenReturn(Optional.of(staffUser));
        when(passwordEncoder.matches("café", "hashed-password")).thenReturn(false);
        when(passwordEncoder.matches("café", "hashed-password")).thenReturn(true);
        when(jwtIssuer.issue(any(), any(), any(), org.mockito.ArgumentMatchers.anyBoolean())).thenReturn("jwt");

        assertThat(service.login("ana@esgtrazabilidad.pe", "café")).isEqualTo("jwt");
    }

    @Test
    void anAdminCannotResetTheirOwnPasswordThatWayAndAWeakOneIsRefused() {
        adminSignedIn();
        assertThatThrownBy(() -> service.resetPassword(admin.getId(), admin.getId(), STRONG))
                .satisfies(thrown -> assertError(thrown, AuthErrors.CANNOT_ACT_ON_OWN_ACCOUNT));

        StaffUser target = StaffUser.create("ana@esgtrazabilidad.pe", "old-hash", "Ana");
        when(staffUserRepository.findById(target.getId())).thenReturn(Optional.of(target));
        assertThatThrownBy(() -> service.resetPassword(admin.getId(), target.getId(), "corta"))
                .satisfies(thrown -> assertError(thrown, AuthErrors.WEAK_PASSWORD));
        verify(staffUserRepository, never()).save(any());
    }

    @Test
    void aStaffMemberCannotResetAnotherPassword() {
        StaffUser staff = StaffUser.create("staff@esgtrazabilidad.pe", "hashed", "Staff");
        when(staffUserRepository.findById(staff.getId())).thenReturn(Optional.of(staff));

        assertThatThrownBy(() -> service.resetPassword(staff.getId(), UUID.randomUUID(), STRONG))
                .satisfies(thrown -> assertError(thrown, AuthErrors.ADMIN_REQUIRED));
    }

    @Test
    void changingYourOwnPasswordNeedsTheCurrentOneAndClearsThePendingFlag() {
        StaffUser me = StaffUser.create("yo@esgtrazabilidad.pe", "old-hash", "Yo", StaffRole.STAFF, true);
        when(staffUserRepository.findById(me.getId())).thenReturn(Optional.of(me));
        when(passwordEncoder.matches("clave-actual-que-tengo", "old-hash")).thenReturn(true);
        when(passwordEncoder.encode(STRONG)).thenReturn("new-hash");

        service.changeOwnPassword(me.getId(), "clave-actual-que-tengo", STRONG);

        verify(staffUserRepository).updatePassword(eq(me.getId()), eq("new-hash"), eq(false), any(Instant.class));
        verify(staffUserRepository, never()).save(any());
    }

    @Test
    void anOldRawPasswordCanStillBeChangedToo() {
        StaffUser me = StaffUser.create("yo@esgtrazabilidad.pe", "old-hash", "Yo");
        when(staffUserRepository.findById(me.getId())).thenReturn(Optional.of(me));
        when(passwordEncoder.matches("café", "old-hash")).thenReturn(false);
        when(passwordEncoder.matches("café", "old-hash")).thenReturn(true);
        when(passwordEncoder.encode(STRONG)).thenReturn("new-hash");

        service.changeOwnPassword(me.getId(), "café", STRONG);

        verify(staffUserRepository).updatePassword(eq(me.getId()), eq("new-hash"), eq(false), any(Instant.class));
    }

    @Test
    void aWrongCurrentPasswordIsAuth008AndNothingIsSaved() {
        StaffUser me = StaffUser.create("yo@esgtrazabilidad.pe", "old-hash", "Yo");
        when(staffUserRepository.findById(me.getId())).thenReturn(Optional.of(me));
        when(passwordEncoder.matches("equivocada", "old-hash")).thenReturn(false);

        assertThatThrownBy(() -> service.changeOwnPassword(me.getId(), "equivocada", STRONG))
                .satisfies(thrown -> assertError(thrown, AuthErrors.WRONG_CURRENT_PASSWORD));
        verify(staffUserRepository, never()).save(any());
    }

    @Test
    void theNewPasswordMustBeStrongAndDifferentFromTheCurrentOne() {
        StaffUser me = StaffUser.create("yo@esgtrazabilidad.pe", "old-hash", "Yo");
        when(staffUserRepository.findById(me.getId())).thenReturn(Optional.of(me));
        when(passwordEncoder.matches(any(), any())).thenReturn(true);

        assertThatThrownBy(() -> service.changeOwnPassword(me.getId(), STRONG, "corta"))
                .satisfies(thrown -> assertError(thrown, AuthErrors.WEAK_PASSWORD));
        assertThatThrownBy(() -> service.changeOwnPassword(me.getId(), STRONG, STRONG))
                .satisfies(thrown -> assertError(thrown, AuthErrors.WEAK_PASSWORD));
        verify(staffUserRepository, never()).save(any());
    }
}
