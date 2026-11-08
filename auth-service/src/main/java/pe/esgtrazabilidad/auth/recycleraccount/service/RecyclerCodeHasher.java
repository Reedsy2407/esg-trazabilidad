package pe.esgtrazabilidad.auth.recycleraccount.service;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * HMAC-SHA256 keyed with {@code RECYCLER_CODE_HMAC_SECRET}, which lives outside the database: a plain
 * hash of a 10-character code is inverted by brute force if the table leaks, an HMAC is not. The same
 * key hashes the client addresses the activation limits count, so those tables hold no address. No
 * default and at least 32 bytes: the service does not start without it. Each use has its own label so
 * a stored value of one kind can never stand for another.
 */
@Component
public class RecyclerCodeHasher {

    static final int MIN_SECRET_BYTES = 32;

    private final SecretKeySpec key;

    RecyclerCodeHasher(@Value("${RECYCLER_CODE_HMAC_SECRET}") String secret) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("RECYCLER_CODE_HMAC_SECRET must be at least " + MIN_SECRET_BYTES + " bytes");
        }
        this.key = new SecretKeySpec(bytes, "HmacSHA256");
    }

    /** The stored form of an activation code (already normalised by {@link ActivationCodes#normalise}). */
    public String code(String normalisedCode) {
        return hmac("activation-code:" + normalisedCode);
    }

    /** For the client address the limits count: no raw address is ever stored. */
    public String address(String clientAddress) {
        return hmac("address:" + clientAddress);
    }

    /** Constant time, so the comparison does not tell how many leading characters matched. */
    public static boolean sameValue(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.US_ASCII), b.getBytes(StandardCharsets.US_ASCII));
    }

    private String hmac(String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            return HexFormat.of().formatHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
