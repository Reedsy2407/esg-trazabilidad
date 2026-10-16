package pe.esgtrazabilidad.auth.staffuser.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import pe.esgtrazabilidad.auth.exception.AuthErrors;
import pe.esgtrazabilidad.kernel.error.ApplicationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordPolicyTest {

    private static final String EMAIL = "ana.perez@asociacion.pe";

    @Test
    void acceptsFifteenCharactersWithNoCompositionRules() {
        assertThat(PasswordPolicy.isAcceptable("abcdfghjkmnpqrs", EMAIL)).isTrue(); // 15 lowercase letters, no digit or symbol
        assertThat(PasswordPolicy.isAcceptable("correcto caballo bateria grapa", EMAIL)).isTrue(); // a passphrase with spaces
    }

    @Test
    void rejectsLessThanFifteenCharacters() {
        assertThat(PasswordPolicy.isAcceptable("a1B2c3D4e5F6g7", EMAIL)).isFalse(); // 14
        assertThat(PasswordPolicy.isAcceptable("", EMAIL)).isFalse();
        assertThat(PasswordPolicy.isAcceptable(null, EMAIL)).isFalse();
    }

    @Test
    void acceptsUpToSixtyFourCharactersAndRejectsMore() {
        assertThat(PasswordPolicy.isAcceptable("kT9".repeat(21) + "k", EMAIL)).isTrue(); // 64
        assertThat(PasswordPolicy.isAcceptable("kT9".repeat(21) + "kk", EMAIL)).isFalse(); // 65
    }

    @Test
    void rejectsAPasswordLongerThanBcryptsSeventyTwoBytesEvenUnderSixtyFourCharacters() {
        // 40 characters, 80 bytes in UTF-8: BCrypt would silently ignore the tail.
        assertThat(PasswordPolicy.isAcceptable("ñ".repeat(40), EMAIL)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "contraseña123456789", // in the common list (case and symbols do not matter)
        "Contraseña-123456789",
        "PASSWORDPASSWORD",
        "qwertyuiopasdfgh",
        "aaaaaaaaaaaaaaaa", // one symbol repeated
        "1234567890123456", // a run
        "abcdefghijklmnop", // a run
        "9876543210987654",
        "mi-plataforma-de-trazabilidad-2026", // the product name
    })
    void rejectsObviousPasswords(String password) {
        assertThat(PasswordPolicy.isAcceptable(password, EMAIL)).isFalse();
    }

    @Test
    void rejectsAPasswordBuiltFromTheEmailsLocalPart() {
        assertThat(PasswordPolicy.isAcceptable("ana.perez-2026-clave", EMAIL)).isFalse();
        assertThat(PasswordPolicy.isAcceptable("clave-de-ANAPEREZ-1", EMAIL)).isFalse();
        // A short local part is not worth matching.
        assertThat(PasswordPolicy.isAcceptable("ana-quiere-un-café-bien-largo", "ana@asociacion.pe")).isTrue();
    }

    @Test
    void normalisesTextSoTheSamePasswordTypedTwoWaysMatches() {
        // "é" as one character and as "e" plus a combining accent (NFKC makes them the same).
        assertThat(PasswordPolicy.normalize("café con leche bien largo"))
                .isEqualTo(PasswordPolicy.normalize("café con leche bien largo"));
        assertThat(PasswordPolicy.normalize(null)).isEmpty();
    }

    @Test
    void validateThrowsWeakPasswordAuth007() {
        assertThatThrownBy(() -> PasswordPolicy.validate("corta", EMAIL))
                .isInstanceOf(ApplicationException.class)
                .satisfies(exception ->
                        assertThat(((ApplicationException) exception).getError()).isEqualTo(AuthErrors.WEAK_PASSWORD));
    }
}
