package pe.esgtrazabilidad.auth.recycleraccount.service;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ActivationCodesAndHasherTest {

    private static String secret(String label) {
        return label + "-built-at-runtime-for-this-test-only";
    }

    @Test
    void aGeneratedCodeHasTenCharactersFromTheAlphabetWithoutAmbiguousSymbols() {
        ActivationCodes codes = new ActivationCodes();
        for (int i = 0; i < 500; i++) {
            String code = codes.next();
            assertThat(code).hasSize(10).matches("[A-HJ-NP-Z2-9]{10}");
            assertThat(ActivationCodes.normalise(code)).isEqualTo(code);
        }
    }

    @Test
    void generatedCodesDoNotRepeatInPractice() {
        ActivationCodes codes = new ActivationCodes();
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 2000; i++) {
            assertThat(seen.add(codes.next())).isTrue();
        }
    }

    @Test
    void normaliseAcceptsAnyCaseAndSeparatorsAndRejectsWhatCannotBeACode() {
        assertThat(ActivationCodes.normalise(" abcde-fghjk ")).isEqualTo("ABCDEFGHJK");
        assertThat(ActivationCodes.normalise("abcde fghjk")).isEqualTo("ABCDEFGHJK");
        assertThat(ActivationCodes.normalise("ABCDEFGHJ")).isNull(); // 9
        assertThat(ActivationCodes.normalise("ABCDEFGHJKL")).isNull(); // 11
        assertThat(ActivationCodes.normalise("ABCDEFGHJ0")).isNull(); // 0 is not in the alphabet
        assertThat(ActivationCodes.normalise("ABCDEFGHJI")).isNull(); // I is not either
        assertThat(ActivationCodes.normalise("")).isNull();
        assertThat(ActivationCodes.normalise(null)).isNull();
    }

    @Test
    void theHmacIsStableKeyedAndSeparatedByLabel() {
        RecyclerCodeHasher one = new RecyclerCodeHasher(secret("uno") + "-0123456789");
        RecyclerCodeHasher other = new RecyclerCodeHasher(secret("otro") + "-0123456789");

        assertThat(one.code("ABCDEFGHJK")).isEqualTo(one.code("ABCDEFGHJK")).hasSize(64);
        assertThat(one.code("ABCDEFGHJK")).isNotEqualTo(one.code("ABCDEFGHJL"));
        assertThat(one.code("ABCDEFGHJK")).isNotEqualTo(other.code("ABCDEFGHJK"));
        assertThat(one.code("x")).isNotEqualTo(one.address("x"));
    }

    @Test
    void refreshTokensAreDerivedPerDeviceAndGenerationAndOnlyTheKeyHolderCanMakeThem() {
        RefreshTokens one = new RefreshTokens(secret("uno") + "-0123456789");
        RefreshTokens other = new RefreshTokens(secret("otro") + "-0123456789");
        java.util.UUID device = java.util.UUID.randomUUID();

        String generationZero = one.derive(device, 0);
        assertThat(generationZero).hasSize(43).matches("[A-Za-z0-9_-]{43}");
        assertThat(one.derive(device, 0)).isEqualTo(generationZero);
        assertThat(one.derive(device, 1)).isNotEqualTo(generationZero);
        assertThat(one.derive(java.util.UUID.randomUUID(), 0)).isNotEqualTo(generationZero);
        assertThat(other.derive(device, 0)).isNotEqualTo(generationZero);
        assertThat(RefreshTokens.hash(generationZero)).hasSize(64).isNotEqualTo(generationZero);
    }

    @Test
    void sameValueComparesWithoutShortCircuiting() {
        assertThat(RecyclerCodeHasher.sameValue("abc", "abc")).isTrue();
        assertThat(RecyclerCodeHasher.sameValue("abc", "abd")).isFalse();
        assertThat(RecyclerCodeHasher.sameValue("abc", "abcd")).isFalse();
    }

    @Test
    void aSecretShorterThanThirtyTwoBytesIsRefused() {
        assertThatThrownBy(() -> new RecyclerCodeHasher("demasiado-corto")).isInstanceOf(IllegalStateException.class);
    }
}
