package pe.esgtrazabilidad.auth.recycleraccount.service;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Refresh tokens: opaque, 256 bits, derived per device and rotation as HMAC-SHA256(server key,
 * "refresh-token:" + device + ":" + generation), and stored only as a SHA-256 hash. Deriving (instead of
 * drawing at random) is what lets a repeat of the previous token inside the grace window be answered with
 * the same current token even though no token is ever kept in clear.
 *
 * <p>The key is {@code JWT_SECRET}, on purpose and not the activation-code key: whoever holds JWT_SECRET
 * can already sign any RECYCLER access token, so deriving refresh tokens from it grants nothing more,
 * while a leak of RECYCLER_CODE_HMAC_SECRET (which only protects code and address hashes) still cannot
 * impersonate anybody. The label keeps these values apart from every other use of the key. Without the
 * key a token is unpredictable even to someone who knows the device id (it travels in the access token)
 * and the generation (a small counter). The hash is a plain SHA-256 because the token is as strong as a
 * key: there is nothing to brute-force, unlike a short code, which gets an HMAC.
 */
@Component
public class RefreshTokens {

    private final SecretKeySpec key;

    RefreshTokens(@Value("${JWT_SECRET}") String secret) {
        this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    String derive(UUID deviceId, int generation) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            byte[] token = mac.doFinal(("refresh-token:" + deviceId + ":" + generation).getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(token);
        } catch (NoSuchAlgorithmException | InvalidKeyException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public static String hash(String token) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
