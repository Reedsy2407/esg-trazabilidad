package pe.esgtrazabilidad.kernel.text;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TextNormalizerTest {

    @Test
    void foldsAccentsCaseAndWhitespace() {
        assertThat(TextNormalizer.normalize("  San Martín   de Porres ")).isEqualTo("SAN MARTIN DE PORRES");
        assertThat(TextNormalizer.normalize("rímac")).isEqualTo(TextNormalizer.normalize("RIMAC"));
    }

    @Test
    void foldsTheEnyeToNSoSigersolsUnaccentedNamesMatch() {
        assertThat(TextNormalizer.normalize("Breña")).isEqualTo("BRENA");
        assertThat(TextNormalizer.normalize("CAÑETE")).isEqualTo(TextNormalizer.normalize("CANETE"));
    }

    @Test
    void treatsANonBreakingSpaceLikeAnyOtherSpace() {
        assertThat(TextNormalizer.normalize("SAN BORJA ")).isEqualTo("SAN BORJA");
    }

    @Test
    void keepsNull() {
        assertThat(TextNormalizer.normalize(null)).isNull();
    }
}
