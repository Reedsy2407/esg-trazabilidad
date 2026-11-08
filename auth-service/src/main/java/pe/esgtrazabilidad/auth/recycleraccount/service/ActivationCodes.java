package pe.esgtrazabilidad.auth.recycleraccount.service;

import java.security.SecureRandom;
import java.util.Locale;

import org.springframework.stereotype.Component;

/**
 * Activation codes: 10 characters from {@link SecureRandom}, handed over in person. The alphabet has 32
 * symbols (letters and digits without I, O, 0 and 1, which are mixed up when read aloud or copied by
 * hand), so each character carries exactly 5 bits and a code 50 bits. That is not enough on its own
 * against guessing: the attempt limits (per address and per code) and the 72 h life are what bound it.
 */
@Component
public class ActivationCodes {

    public static final int LENGTH = 10;
    static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final SecureRandom random = new SecureRandom();

    String next() {
        StringBuilder code = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            code.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return code.toString();
    }

    /**
     * What a person may type: any case, with spaces or hyphens between groups. Returns the canonical form,
     * or null when it cannot be a code (wrong length or a symbol outside the alphabet), which callers
     * answer exactly like a wrong code.
     */
    public static String normalise(String typed) {
        if (typed == null) {
            return null;
        }
        String canonical = typed.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
        if (canonical.length() != LENGTH) {
            return null;
        }
        for (int i = 0; i < canonical.length(); i++) {
            if (ALPHABET.indexOf(canonical.charAt(i)) < 0) {
                return null;
            }
        }
        return canonical;
    }
}
