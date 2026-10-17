package pe.esgtrazabilidad.auth.passwordreset.service;

import java.security.SecureRandom;

import org.springframework.stereotype.Component;

/** 8 decimal digits from {@link SecureRandom}; leading zeros are kept (26.6 bits, so the limits matter). */
@Component
class CodeGenerator {

    static final int LENGTH = 8;
    private static final int BOUND = 100_000_000;

    private final SecureRandom random = new SecureRandom();

    String next() {
        return String.format("%0" + LENGTH + "d", random.nextInt(BOUND));
    }
}
