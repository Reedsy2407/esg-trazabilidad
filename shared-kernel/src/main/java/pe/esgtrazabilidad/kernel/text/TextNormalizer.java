package pe.esgtrazabilidad.kernel.text;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Folds text for comparison only: strips accents (Ñ becomes N), upper-cases
 * and collapses whitespace, non-breaking spaces included. "Rímac", "RIMAC" and " rimac " all compare equal.
 * Never use the result for storage or display.
 */
public final class TextNormalizer {

    private TextNormalizer() {
    }

    public static String normalize(String text) {
        if (text == null) {
            return null;
        }
        String withoutMarks = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return withoutMarks.toUpperCase(Locale.ROOT).replaceAll("[\\s\\p{Z}]+", " ").strip();
    }
}
