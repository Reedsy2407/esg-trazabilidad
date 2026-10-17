package pe.esgtrazabilidad.auth.passwordreset.service;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * HMAC-SHA256 keyed with {@code PASSWORD_RESET_PEPPER}. A plain hash of an 8-digit code (10^8 values)
 * is inverted in seconds if the table leaks; with a key that lives outside the database it is not.
 * Codes are bound to the account and to the code's own record, so a stored value cannot be moved to
 * another account. Emails and addresses are hashed too, so the limit tables hold neither. The pepper
 * has no default and at least 32 bytes: the service does not start without it.
 */
@Component
class CodeHasher {

    static final int MIN_PEPPER_BYTES = 32;

    private final SecretKeySpec key;

    CodeHasher(@Value("${PASSWORD_RESET_PEPPER}") String pepper) {
        byte[] bytes = pepper.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_PEPPER_BYTES) {
            throw new IllegalStateException("PASSWORD_RESET_PEPPER must be at least " + MIN_PEPPER_BYTES + " bytes");
        }
        this.key = new SecretKeySpec(bytes, "HmacSHA256");
    }

    String code(UUID staffUserId, UUID codeId, String code) {
        return hmac("code:" + staffUserId + ":" + codeId + ":" + code);
    }

    /** For the email and the address the limits count: {@code label} is "email" or "ip". */
    String label(String label, String value) {
        return hmac(label + ":" + value);
    }

    /** Constant time, so the comparison does not tell how many leading characters matched. */
    boolean matches(UUID staffUserId, UUID codeId, String typedCode, String storedHmac) {
        return MessageDigest.isEqual(
                code(staffUserId, codeId, typedCode).getBytes(StandardCharsets.US_ASCII),
                storedHmac.getBytes(StandardCharsets.US_ASCII));
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
