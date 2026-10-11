package pe.esgtrazabilidad.kernel.ruc;

import java.util.Set;

/**
 * A Peruvian RUC that passed the local checks: 11 ASCII digits, a prefix of
 * 10, 15, 16, 17 or 20, and the modulo-11 check digit (weights
 * 5,4,3,2,7,6,5,4,3,2 over the first 10 digits; r = 11 - sum mod 11, where
 * 10 becomes 0 and 11 becomes 1).
 *
 * <p>Local validation only: it says the number is well formed, not that
 * SUNAT has it. Each service maps an invalid RUC to its own error code
 * (RPT-010, ASO-004), so this class only exposes a boolean check and a
 * factory that refuses with a fixed message.
 */
public record Ruc(String value) {

    private static final int[] WEIGHTS = {5, 4, 3, 2, 7, 6, 5, 4, 3, 2};
    private static final Set<String> PREFIXES = Set.of("10", "15", "16", "17", "20");

    public Ruc {
        if (!isValid(value)) {
            throw new IllegalArgumentException("El RUC no es válido");
        }
    }

    public static Ruc of(String value) {
        return new Ruc(value);
    }

    public static boolean isValid(String value) {
        if (value == null || value.length() != 11 || !isAsciiDigits(value)) {
            return false;
        }
        if (!PREFIXES.contains(value.substring(0, 2))) {
            return false;
        }
        return computeCheckDigit(value.substring(0, 10)) == value.charAt(10) - '0';
    }

    /** The check digit for the first 10 digits of a RUC (which must be 10 ASCII digits). */
    static int computeCheckDigit(String firstTenDigits) {
        int sum = 0;
        for (int i = 0; i < WEIGHTS.length; i++) {
            sum += (firstTenDigits.charAt(i) - '0') * WEIGHTS[i];
        }
        int r = 11 - sum % 11;
        return r == 10 ? 0 : r == 11 ? 1 : r;
    }

    private static boolean isAsciiDigits(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }
}
