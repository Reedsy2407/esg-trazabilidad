package pe.esgtrazabilidad.reporting.it.support;

/** RUCs that pass the real check digit, for tests that go through the register endpoints. */
public final class TestRucs {

    private static final int[] WEIGHTS = {5, 4, 3, 2, 7, 6, 5, 4, 3, 2};

    private TestRucs() {
    }

    /** The first ten digits plus their modulo-11 check digit. */
    public static String withCheckDigit(String firstTenDigits) {
        int sum = 0;
        for (int i = 0; i < WEIGHTS.length; i++) {
            sum += (firstTenDigits.charAt(i) - '0') * WEIGHTS[i];
        }
        int r = 11 - sum % 11;
        int digit = r == 10 ? 0 : r == 11 ? 1 : r;
        return firstTenDigits + digit;
    }

    /** A valid 11-digit RUC (prefix 20) whose first ten digits are {@code 20} followed by the 8-digit sequence. */
    public static String fromSequence(long sequence) {
        return withCheckDigit(String.format("20%08d", sequence));
    }
}
