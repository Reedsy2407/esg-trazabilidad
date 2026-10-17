package pe.esgtrazabilidad.auth.passwordreset.service;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CodeGeneratorAndHasherTest {

    private static final String PEPPER = "una-pimienta-de-prueba-de-al-menos-32-bytes";

    @Test
    void aCodeIsEightDigitsAndLeadingZerosAreKept() {
        CodeGenerator generator = new CodeGenerator();
        Set<String> seen = new HashSet<>();
        boolean sawLeadingZero = false;
        int[] perDigit = new int[10];
        for (int i = 0; i < 20_000; i++) {
            String code = generator.next();
            assertThat(code).matches("\\d{8}");
            sawLeadingZero |= code.startsWith("0");
            perDigit[code.charAt(0) - '0']++;
            seen.add(code);
        }
        assertThat(sawLeadingZero).as("leading zeros are valid codes").isTrue();
        assertThat(seen.size()).as("no obvious repetition").isGreaterThan(19_900);
        // The first digit is uniform: each of the ten digits lands near 2,000 of 20,000 (very wide bounds).
        for (int count : perDigit) {
            assertThat(count).isBetween(1_700, 2_300);
        }
    }

    @Test
    void theHmacIsDeterministicAndDependsOnThePepperTheAccountTheRecordAndTheCode() {
        CodeHasher hasher = new CodeHasher(PEPPER);
        UUID user = UUID.randomUUID();
        UUID record = UUID.randomUUID();

        String hmac = hasher.code(user, record, "12345678");

        assertThat(hmac).matches("[0-9a-f]{64}").isEqualTo(hasher.code(user, record, "12345678"));
        assertThat(new CodeHasher(PEPPER + "x").code(user, record, "12345678")).isNotEqualTo(hmac);
        assertThat(hasher.code(UUID.randomUUID(), record, "12345678")).isNotEqualTo(hmac);
        assertThat(hasher.code(user, UUID.randomUUID(), "12345678")).isNotEqualTo(hmac);
        assertThat(hasher.code(user, record, "12345679")).isNotEqualTo(hmac);
    }

    @Test
    void matchesAcceptsTheRightCodeAndRefusesAnyOther() {
        CodeHasher hasher = new CodeHasher(PEPPER);
        UUID user = UUID.randomUUID();
        UUID record = UUID.randomUUID();
        String stored = hasher.code(user, record, "00004321");

        assertThat(hasher.matches(user, record, "00004321", stored)).isTrue();
        assertThat(hasher.matches(user, record, "00004322", stored)).isFalse();
        assertThat(hasher.matches(UUID.randomUUID(), record, "00004321", stored)).isFalse();
    }

    @Test
    void emailsAndAddressesAreHashedUnderTheirOwnLabels() {
        CodeHasher hasher = new CodeHasher(PEPPER);

        assertThat(hasher.label("email", "a@b.pe")).isEqualTo(hasher.label("email", "a@b.pe"));
        assertThat(hasher.label("email", "a@b.pe")).isNotEqualTo(hasher.label("ip", "a@b.pe"));
        assertThat(hasher.label("email", "a@b.pe")).doesNotContain("a@b.pe");
    }

    @Test
    void aPepperShorterThan32BytesIsRefused() {
        assertThatThrownBy(() -> new CodeHasher("demasiado-corta")).isInstanceOf(IllegalStateException.class);
    }
}
