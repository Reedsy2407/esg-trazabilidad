package pe.esgtrazabilidad.auth.passwordreset.service;

import java.security.SecureRandom;

import org.springframework.stereotype.Component;

/** 8 decimal digits from {@link SecureRandom}; leading zeros are kept (26.6 bits, so the limits matter). */
@Component
class CodeGenerator {

    static final int LENGTH = 8;
    private static final int BOUND = 100_000_000;
    private static final int TOKEN_BYTES = 32;

    private final SecureRandom random = new SecureRandom();

    String next() {
        return String.format("%0" + LENGTH + "d", random.nextInt(BOUND));
    }

    /** 32 random bytes (256 bits) as 43 URL-safe characters: the secret part of a reset token. */
    String tokenSecret() {
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
