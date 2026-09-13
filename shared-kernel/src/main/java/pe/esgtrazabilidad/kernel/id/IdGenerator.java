package pe.esgtrazabilidad.kernel.id;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * Generates time-ordered UUID v7 values per RFC 9562: a 48-bit millisecond
 * timestamp followed by version/variant bits and random fill.
 */
public final class IdGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final long VERSION_7 = 0x7L << 12;
    private static final long VARIANT_RFC4122 = 0b10L << 62;
    private static final long RAND_A_MASK = 0x0FFFL;
    private static final long RAND_B_MASK = 0x3FFFFFFFFFFFFFFFL;

    private IdGenerator() {
    }

    public static UUID generate() {
        long timestamp = System.currentTimeMillis() & 0xFFFFFFFFFFFFL;

        long mostSigBits = (timestamp << 16) | VERSION_7 | (RANDOM.nextLong() & RAND_A_MASK);
        long leastSigBits = VARIANT_RFC4122 | (RANDOM.nextLong() & RAND_B_MASK);

        return new UUID(mostSigBits, leastSigBits);
    }
}
