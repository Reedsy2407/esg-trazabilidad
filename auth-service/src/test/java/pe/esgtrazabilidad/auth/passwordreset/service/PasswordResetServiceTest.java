package pe.esgtrazabilidad.auth.passwordreset.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import pe.esgtrazabilidad.auth.exception.AuthErrors;
import pe.esgtrazabilidad.auth.passwordreset.port.out.PasswordResetStore;
import pe.esgtrazabilidad.auth.passwordreset.port.out.PasswordResetStore.CheckResult;
import pe.esgtrazabilidad.auth.passwordreset.port.out.PasswordResetStore.Kind;
import pe.esgtrazabilidad.auth.passwordreset.port.out.PasswordResetStore.LiveCode;
import pe.esgtrazabilidad.auth.passwordreset.port.out.PasswordResetStore.Outcome;
import pe.esgtrazabilidad.auth.staffuser.domain.StaffUser;
import pe.esgtrazabilidad.auth.staffuser.port.out.StaffUserRepository;
import pe.esgtrazabilidad.kernel.error.ApplicationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PasswordResetServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-16T15:00:00Z");
    private static final String EMAIL = "ana@esgtrazabilidad.pe";
    private static final String STRONG = "una-clave-larga-y-segura";
    private static final String IP = "203.0.113.7";

    @Mock
    private StaffUserRepository staffUsers;

    @Mock
    private PasswordResetStore store;

    @Mock
    private CodeGenerator generator;

    @Mock
    private EmailDispatcher dispatcher;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TransactionTemplate transaction;

    private final CodeHasher hasher = new CodeHasher("una-pimienta-de-prueba-de-al-menos-32-bytes");
    private StaffUser account;

    @BeforeEach
    void setUp() {
        account = StaffUser.create(EMAIL, "current-hash", "Ana");
        when(generator.next()).thenReturn("12345678");
        when(staffUsers.findByEmail(EMAIL)).thenReturn(Optional.of(account));
        when(store.recordRequest(any(), anyString(), anyString(), any(), anyBoolean())).thenReturn(UUID.randomUUID());
        when(transaction.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
    }

    private PasswordResetService service(boolean enabled) {
        return new PasswordResetService(
                staffUsers, store, hasher, generator, dispatcher, passwordEncoder, transaction,
                Clock.fixed(NOW, ZoneOffset.UTC), enabled);
    }

    private static void assertError(Throwable thrown, AuthErrors expected) {
        assertThat(thrown).isInstanceOf(ApplicationException.class);
        assertThat(((ApplicationException) thrown).getError()).isEqualTo(expected);
    }

    // --- request -----------------------------------------------------------------------------------

    @Test
    void anActiveAccountGetsACodeStoredAsAnHmacAndSentOnce() {
        service(true).request("  Ana@EsgTrazabilidad.pe ", IP);

        ArgumentCaptor<UUID> codeId = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<String> stored = ArgumentCaptor.forClass(String.class);
        verify(store).replaceCode(codeId.capture(), eq(account.getId()), stored.capture(), eq(NOW), eq(NOW.plusSeconds(600)));
        assertThat(stored.getValue()).isEqualTo(hasher.code(account.getId(), codeId.getValue(), "12345678"));
        assertThat(stored.getValue()).doesNotContain("12345678");
        verify(dispatcher).resetCode(any(), eq(EMAIL), eq("12345678"));
        verify(store, never()).replaceCodeDecoy();
    }

    @Test
    void anUnknownAnInactiveAndALimitedAccountDoTheSameWorkAndSendNothing() {
        when(staffUsers.findByEmail("nadie@esgtrazabilidad.pe")).thenReturn(Optional.empty());
        PasswordResetService service = service(true);
        service.request("nadie@esgtrazabilidad.pe", IP);

        StaffUser inactive = account.deactivate("baja", UUID.randomUUID(), NOW);
        when(staffUsers.findByEmail(EMAIL)).thenReturn(Optional.of(inactive));
        service.request(EMAIL, IP);

        verify(dispatcher, never()).resetCode(any(), anyString(), anyString());
        verify(store, never()).replaceCode(any(), any(), anyString(), any(), any());
        // Same steps as a real request: purge, both limits, the cap, a recorded row, and the decoy write.
        verify(store, org.mockito.Mockito.times(2)).purgeOlderThan(any());
        verify(store, org.mockito.Mockito.times(2)).acceptedByIpSince(eq(Kind.REQUEST), anyString(), any());
        verify(store, org.mockito.Mockito.times(2)).acceptedByEmailSince(eq(Kind.REQUEST), anyString(), any());
        verify(store, org.mockito.Mockito.times(2)).sentSince(any());
        verify(store, org.mockito.Mockito.times(2)).recordRequest(eq(Kind.REQUEST), anyString(), anyString(), eq(NOW), eq(true));
        verify(store, org.mockito.Mockito.times(2)).replaceCodeDecoy();
    }

    @Test
    void threeRequestsAnHourOrOneInTheLastMinuteLimitTheEmailWithoutAnError() {
        PasswordResetService service = service(true);
        when(store.acceptedByEmailSince(eq(Kind.REQUEST), anyString(), any()))
                .thenReturn(List.of(NOW.minusSeconds(3000), NOW.minusSeconds(2000), NOW.minusSeconds(1000)));
        service.request(EMAIL, IP);

        when(store.acceptedByEmailSince(eq(Kind.REQUEST), anyString(), any())).thenReturn(List.of(NOW.minusSeconds(30)));
        service.request(EMAIL, IP);

        verify(dispatcher, never()).resetCode(any(), anyString(), anyString());
        // A limited request is recorded as not accepted, so spamming cannot extend its own lockout.
        verify(store, org.mockito.Mockito.times(2)).recordRequest(eq(Kind.REQUEST), anyString(), anyString(), eq(NOW), eq(false));
    }

    @Test
    void aRequestOlderThanAMinuteWithFewerThanThreeIsAllowed() {
        when(store.acceptedByEmailSince(eq(Kind.REQUEST), anyString(), any()))
                .thenReturn(List.of(NOW.minusSeconds(61), NOW.minusSeconds(900)));

        service(true).request(EMAIL, IP);

        verify(dispatcher).resetCode(any(), eq(EMAIL), eq("12345678"));
    }

    @Test
    void theDailyCapOfEightySendsStopsTheEmailButStillAnswersTheSame() {
        when(store.sentSince(any())).thenReturn(80);

        service(true).request(EMAIL, IP);

        verify(dispatcher, never()).resetCode(any(), anyString(), anyString());
        verify(store).replaceCodeDecoy();
    }

    @Test
    void theEmergencySwitchSendsNothingAndStoresNoCode() {
        service(false).request(EMAIL, IP);

        verify(dispatcher, never()).resetCode(any(), anyString(), anyString());
        verify(store, never()).replaceCode(any(), any(), anyString(), any(), any());
    }

    @Test
    void tenRequestsAnHourFromOneAddressAreAnErrorWithRetryAfterAboutTheOldestOne() {
        when(store.acceptedByIpSince(eq(Kind.REQUEST), anyString(), any()))
                .thenReturn(java.util.Collections.nCopies(10, NOW.minusSeconds(600)));

        assertThatThrownBy(() -> service(true).request(EMAIL, IP))
                .isInstanceOfSatisfying(ResetRateLimitedException.class, thrown -> {
                    assertError(thrown, AuthErrors.TOO_MANY_RESET_ATTEMPTS);
                    assertThat(thrown.getRetryAfterSeconds()).isBetween(2990L, 3010L);
                });
        verify(dispatcher, never()).resetCode(any(), anyString(), anyString());
    }

    @Test
    void aSecondRequestThatLosesTheRaceForTheLiveCodeStillAnswersNormallyAndSendsNothing() {
        org.mockito.Mockito.doThrow(new DuplicateKeyException("uq_password_reset_code_live"))
                .when(store).replaceCode(any(), any(), anyString(), any(), any());

        service(true).request(EMAIL, IP);

        verify(dispatcher, never()).resetCode(any(), anyString(), anyString());
    }

    // --- verify ------------------------------------------------------------------------------------

    private static final String SECRET = "s".repeat(43);

    private void liveCode(UUID accountId, Outcome outcome, UUID codeId) {
        when(store.check(eq(accountId), eq(NOW), eq(5), any())).thenReturn(new CheckResult(outcome, codeId));
    }

    private void unknownAccountHasNoCode() {
        when(store.check(eq(new UUID(0L, 0L)), eq(NOW), eq(5), any())).thenReturn(new CheckResult(Outcome.NONE, null));
    }

    @Test
    void theRightCodeIsSpentAndAnHmacOnlyTokenBoundToTheAccountIsStored() {
        UUID codeId = UUID.randomUUID();
        liveCode(account.getId(), Outcome.MATCH, codeId);
        when(generator.tokenSecret()).thenReturn(SECRET);
        when(store.consume(codeId, NOW)).thenReturn(true);

        String token = service(true).verify(EMAIL, "1234 5678", IP);

        ArgumentCaptor<UUID> tokenId = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<String> hmac = ArgumentCaptor.forClass(String.class);
        verify(store).replaceToken(tokenId.capture(), eq(account.getId()), hmac.capture(), eq(NOW), eq(NOW.plusSeconds(600)));
        assertThat(token).isEqualTo(tokenId.getValue() + "." + SECRET);
        assertThat(hmac.getValue()).isEqualTo(hasher.token(account.getId(), tokenId.getValue(), SECRET)).doesNotContain(SECRET);
        verify(dispatcher, never()).passwordChanged(any(), anyString(), any());
    }

    @Test
    void theMatcherAcceptsOnlyTheCodeThatWasStoredForThatRecord() {
        liveCode(account.getId(), Outcome.MISMATCH, UUID.randomUUID());
        assertThatThrownBy(() -> service(true).verify(EMAIL, "12345678", IP))
                .satisfies(thrown -> assertError(thrown, AuthErrors.INVALID_RESET_CODE));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Predicate<LiveCode>> matcher = ArgumentCaptor.forClass(Predicate.class);
        verify(store).check(eq(account.getId()), eq(NOW), eq(5), matcher.capture());
        UUID codeId = UUID.randomUUID();
        assertThat(matcher.getValue().test(new LiveCode(codeId, hasher.code(account.getId(), codeId, "12345678")))).isTrue();
        assertThat(matcher.getValue().test(new LiveCode(codeId, hasher.code(account.getId(), codeId, "87654321")))).isFalse();
    }

    @Test
    void everyKindOfFailureIsTheSameErrorAndGoesThroughTheSameCheck() {
        liveCode(account.getId(), Outcome.MISMATCH, UUID.randomUUID());
        assertThatThrownBy(() -> service(true).verify(EMAIL, "00000000", IP))
                .satisfies(thrown -> assertError(thrown, AuthErrors.INVALID_RESET_CODE));

        liveCode(account.getId(), Outcome.NONE, null);
        assertThatThrownBy(() -> service(true).verify(EMAIL, "00000000", IP))
                .satisfies(thrown -> assertError(thrown, AuthErrors.INVALID_RESET_CODE));

        unknownAccountHasNoCode();
        when(staffUsers.findByEmail("nadie@esgtrazabilidad.pe")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service(true).verify("nadie@esgtrazabilidad.pe", "00000000", IP))
                .satisfies(thrown -> assertError(thrown, AuthErrors.INVALID_RESET_CODE));

        when(staffUsers.findByEmail(EMAIL)).thenReturn(Optional.of(account.deactivate("baja", UUID.randomUUID(), NOW)));
        assertThatThrownBy(() -> service(true).verify(EMAIL, "00000000", IP))
                .satisfies(thrown -> assertError(thrown, AuthErrors.INVALID_RESET_CODE));

        // A malformed code is not special-cased either: it is checked (and counted) like any wrong code.
        when(staffUsers.findByEmail(EMAIL)).thenReturn(Optional.of(account));
        liveCode(account.getId(), Outcome.MISMATCH, UUID.randomUUID());
        assertThatThrownBy(() -> service(true).verify(EMAIL, "12ab5678", IP))
                .satisfies(thrown -> assertError(thrown, AuthErrors.INVALID_RESET_CODE));

        verify(store, never()).replaceToken(any(), any(), anyString(), any(), any());
        verify(store, org.mockito.Mockito.times(5)).check(any(), any(), org.mockito.ArgumentMatchers.anyInt(), any());
    }

    @Test
    void ifSomebodyElseSpentTheCodeFirstNoTokenIsIssued() {
        UUID codeId = UUID.randomUUID();
        liveCode(account.getId(), Outcome.MATCH, codeId);
        when(store.consume(codeId, NOW)).thenReturn(false);

        assertThatThrownBy(() -> service(true).verify(EMAIL, "12345678", IP))
                .satisfies(thrown -> assertError(thrown, AuthErrors.INVALID_RESET_CODE));

        verify(store, never()).replaceToken(any(), any(), anyString(), any(), any());
    }

    @Test
    void twentyVerificationsAnHourFromOneAddressAreRefusedBeforeAnythingIsChecked() {
        when(store.acceptedByIpSince(eq(Kind.VERIFY), anyString(), any()))
                .thenReturn(java.util.Collections.nCopies(20, NOW.minusSeconds(60)));

        assertThatThrownBy(() -> service(true).verify(EMAIL, "12345678", IP))
                .isInstanceOf(ResetRateLimitedException.class);

        verify(store, never()).check(any(), any(), org.mockito.ArgumentMatchers.anyInt(), any());
    }

    // --- confirm -----------------------------------------------------------------------------------

    private String liveToken(UUID tokenId) {
        when(store.findLiveToken(tokenId, NOW))
                .thenReturn(Optional.of(new PasswordResetStore.LiveToken(account.getId(), hasher.token(account.getId(), tokenId, SECRET))));
        when(staffUsers.findById(account.getId())).thenReturn(Optional.of(account));
        return tokenId + "." + SECRET;
    }

    @Test
    void theTokenIsSpentThePasswordChangedLiveCodesSpentAndTheNoticeSent() {
        UUID tokenId = UUID.randomUUID();
        String token = liveToken(tokenId);
        when(passwordEncoder.matches(anyString(), eq("current-hash"))).thenReturn(false);
        when(passwordEncoder.encode(STRONG)).thenReturn("new-hash");
        when(store.consumeToken(tokenId, NOW)).thenReturn(true);

        service(true).confirm(token, STRONG, IP);

        verify(staffUsers).updatePassword(account.getId(), "new-hash", false, NOW);
        verify(store).spendLiveCodes(account.getId(), NOW);
        verify(store).recordRequest(eq(Kind.NOTICE), anyString(), anyString(), eq(NOW), eq(true));
        verify(dispatcher).passwordChanged(any(), eq(EMAIL), eq(NOW));
    }

    @Test
    void aTokenThatDoesNotWorkIsAlwaysTheSameError() {
        UUID tokenId = UUID.randomUUID();
        // Unknown, expired or spent: the store finds no live token.
        when(store.findLiveToken(tokenId, NOW)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service(true).confirm(tokenId + "." + SECRET, STRONG, IP))
                .satisfies(thrown -> assertError(thrown, AuthErrors.INVALID_RESET_CODE));

        // Right id, wrong secret.
        liveToken(tokenId);
        assertThatThrownBy(() -> service(true).confirm(tokenId + "." + "x".repeat(43), STRONG, IP))
                .satisfies(thrown -> assertError(thrown, AuthErrors.INVALID_RESET_CODE));

        // Malformed.
        for (String malformed : new String[] {"sin-punto", "no-uuid." + SECRET, ".", ""}) {
            assertThatThrownBy(() -> service(true).confirm(malformed, STRONG, IP))
                    .satisfies(thrown -> assertError(thrown, AuthErrors.INVALID_RESET_CODE));
        }

        // The account was deactivated after the token was issued.
        when(staffUsers.findById(account.getId())).thenReturn(Optional.of(account.deactivate("baja", UUID.randomUUID(), NOW)));
        assertThatThrownBy(() -> service(true).confirm(tokenId + "." + SECRET, STRONG, IP))
                .satisfies(thrown -> assertError(thrown, AuthErrors.INVALID_RESET_CODE));

        verify(store, never()).consumeToken(any(), any());
        verify(staffUsers, never()).updatePassword(any(), anyString(), anyBoolean(), any());
    }

    @Test
    void aWeakOrRepeatedPasswordIsRefusedAndDoesNotSpendTheToken() {
        UUID tokenId = UUID.randomUUID();
        String token = liveToken(tokenId);
        assertThatThrownBy(() -> service(true).confirm(token, "corta", IP))
                .satisfies(thrown -> assertError(thrown, AuthErrors.WEAK_PASSWORD));

        when(passwordEncoder.matches(STRONG, "current-hash")).thenReturn(true);
        assertThatThrownBy(() -> service(true).confirm(token, STRONG, IP))
                .satisfies(thrown -> assertError(thrown, AuthErrors.WEAK_PASSWORD));

        verify(store, never()).consumeToken(any(), any());
    }

    @Test
    void ifSomebodyElseSpentTheTokenFirstThePasswordIsNotWritten() {
        UUID tokenId = UUID.randomUUID();
        String token = liveToken(tokenId);
        when(passwordEncoder.encode(STRONG)).thenReturn("new-hash");
        when(store.consumeToken(tokenId, NOW)).thenReturn(false);

        assertThatThrownBy(() -> service(true).confirm(token, STRONG, IP))
                .satisfies(thrown -> assertError(thrown, AuthErrors.INVALID_RESET_CODE));

        verify(staffUsers, never()).updatePassword(any(), anyString(), anyBoolean(), any());
        verify(dispatcher, never()).passwordChanged(any(), anyString(), any());
    }

    @Test
    void twentyConfirmationsAnHourFromOneAddressAreRefusedBeforeAnythingIsLookedAt() {
        when(store.acceptedByIpSince(eq(Kind.CONFIRM), anyString(), any()))
                .thenReturn(java.util.Collections.nCopies(20, NOW.minusSeconds(60)));

        assertThatThrownBy(() -> service(true).confirm(UUID.randomUUID() + "." + SECRET, STRONG, IP))
                .isInstanceOf(ResetRateLimitedException.class);

        verify(store, never()).findLiveToken(any(), any());
    }

    // --- the notice --------------------------------------------------------------------------------

    @Test
    void theNoticeIsSkippedAtTheDailyCapAndWhenRecoveryIsSwitchedOffAndNeverFailsTheChange() {
        when(store.sentSince(any())).thenReturn(80);
        service(true).passwordChanged(EMAIL, NOW);
        service(false).passwordChanged(EMAIL, NOW);
        verify(dispatcher, never()).passwordChanged(any(), anyString(), any());

        when(store.sentSince(any())).thenThrow(new IllegalStateException("db down"));
        service(true).passwordChanged(EMAIL, NOW); // does not throw
    }
}
