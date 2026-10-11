package pe.esgtrazabilidad.kernel.ruc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RucTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "20131312955", // a real, public RUC
            "20999000011", // the demo association's RUC, already in production
            "10000000014", // one per allowed prefix: 10, 15, 16, 17
            "15000000016",
            "16000000004",
            "17000000001",
    })
    void acceptsRucsWithAnAllowedPrefixAndTheRightCheckDigit(String value) {
        assertThat(Ruc.isValid(value)).isTrue();
        assertThat(Ruc.of(value).value()).isEqualTo(value);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "20999000029", // demo company RUC: its check digit should be 0
            "20999000037", // demo company RUC: its check digit should be 8
            "20131312954", // a real RUC with its last digit changed
    })
    void rejectsAWrongCheckDigit(String value) {
        assertThat(Ruc.isValid(value)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"11131312955", "30131312955", "00000000000", "18131312955"})
    void rejectsAPrefixOtherThanTenFifteenSixteenSeventeenOrTwenty(String value) {
        assertThat(Ruc.isValid(value)).isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"2013131295", "201313129555", "2013131295a", " 20131312955", "20131312955 ", "２０131312955"})
    void rejectsAnythingThatIsNotExactlyElevenAsciiDigits(String value) {
        assertThat(Ruc.isValid(value)).isFalse();
    }

    @Test
    void checkDigitIsElevenMinusTheWeightedSumModElevenWithTenAndElevenFolded() {
        // Weights 5,4,3,2,7,6,5,4,3,2 over the first 10 digits.
        assertThat(Ruc.computeCheckDigit("2013131295")).isEqualTo(5);  // sum 94, 94 mod 11 = 6, r = 5
        assertThat(Ruc.computeCheckDigit("2099900002")).isZero();       // sum 122, mod 11 = 1, r = 10 -> 0
        assertThat(Ruc.computeCheckDigit("1700000000")).isEqualTo(1);  // sum 33, mod 11 = 0, r = 11 -> 1
    }

    @Test
    void ofRefusesAnInvalidRuc() {
        assertThatThrownBy(() -> Ruc.of("20999000029"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("El RUC no es válido");
    }
}
