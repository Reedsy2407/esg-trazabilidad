package pe.esgtrazabilidad.auth.staffuser.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import pe.esgtrazabilidad.auth.exception.AuthErrors;
import pe.esgtrazabilidad.kernel.error.ApplicationException;

/**
 * NIST SP 800-63B for a password that is the only authenticator: at least 15 characters, up to 64
 * accepted, any character allowed (no composition rules, no forced rotation), compared against a list of
 * common passwords. The top limit is BCrypt's: it only reads the first 72 bytes, so a password
 * that long in UTF-8 is refused instead of being silently cut. Text is normalised (NFKC) before
 * hashing, in login too, so the same password typed on another keyboard layout still matches.
 *
 * <p>The blocklist is a short list kept in {@code common-passwords.txt}, not the full breached-password
 * corpora: an external lookup would add a dependency (and send hash prefixes to a third party) for a
 * handful of staff accounts. It is meant to catch the obvious, not to replace a password manager.
 */
final class PasswordPolicy {

    static final int MIN_LENGTH = 15;
    static final int MAX_LENGTH = 64;
    private static final int BCRYPT_MAX_BYTES = 72;
    private static final Set<String> COMMON = loadCommon();

    private PasswordPolicy() {}

    /** Applied to the password at every point it is read: creation, change, reset and login. */
    static String normalize(String raw) {
        return raw == null ? "" : Normalizer.normalize(raw, Normalizer.Form.NFKC);
    }

    /** Throws WEAK_PASSWORD (AUTH-007) when the (already normalised) password is not acceptable. */
    static void validate(String password, String email) {
        if (!isAcceptable(password, email)) {
            throw new ApplicationException(AuthErrors.WEAK_PASSWORD);
        }
    }

    static boolean isAcceptable(String password, String email) {
        if (password == null) {
            return false;
        }
        int length = password.codePointCount(0, password.length());
        if (length < MIN_LENGTH || length > MAX_LENGTH) {
            return false;
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
            return false;
        }
        String lower = password.toLowerCase(Locale.ROOT);
        String compact = lower.replaceAll("[^\\p{L}\\p{N}]", "");
        if (COMMON.contains(compact) || isOneSymbolRepeated(compact) || isRun(compact)) {
            return false;
        }
        if (compact.contains("trazabilidad")) {
            return false;
        }
        String localPart = email == null ? "" : email.toLowerCase(Locale.ROOT).split("@", 2)[0];
        String compactLocal = localPart.replaceAll("[^\\p{L}\\p{N}]", "");
        return compactLocal.length() < 4 || !compact.contains(compactLocal);
    }

    private static boolean isOneSymbolRepeated(String s) {
        return !s.isEmpty() && s.chars().allMatch(c -> c == s.charAt(0));
    }

    /** "123456789012345", "abcdefghijklmno": each symbol is the previous one plus one (or minus one). */
    private static boolean isRun(String s) {
        if (s.length() < 2) {
            return false;
        }
        int step = s.charAt(1) - s.charAt(0);
        if (Math.abs(step) != 1) {
            return false;
        }
        for (int i = 1; i < s.length(); i++) {
            if (s.charAt(i) - s.charAt(i - 1) != step) {
                return false;
            }
        }
        return true;
    }

    private static Set<String> loadCommon() {
        try (InputStream in = PasswordPolicy.class.getResourceAsStream("/common-passwords.txt")) {
            if (in == null) {
                throw new IllegalStateException("common-passwords.txt is missing from the classpath");
            }
            Set<String> out = new HashSet<>();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("#")) {
                        continue;
                    }
                    String entry = line.strip().toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
                    if (!entry.isEmpty()) {
                        out.add(entry);
                    }
                }
            }
            return out;
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
