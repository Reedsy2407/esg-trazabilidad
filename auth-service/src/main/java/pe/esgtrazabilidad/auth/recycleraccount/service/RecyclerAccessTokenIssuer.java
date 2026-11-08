package pe.esgtrazabilidad.auth.recycleraccount.service;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import pe.esgtrazabilidad.auth.recycleraccount.domain.RecyclerAccount;

/**
 * The RECYCLER access token (A.1): 15 minutes, HS256 with the shared JWT_SECRET, claims {@code sub}
 * (account), {@code role}, {@code rid}, {@code aid}, {@code did} and, only with a consent accepted at the
 * current version, {@code cv} (and {@code lc=true} if location was accepted too). It never carries the
 * recycler's name or any personal data. The association comes from the live status copy.
 */
@Component
public class RecyclerAccessTokenIssuer {

    public static final Duration LIFETIME = Duration.ofMinutes(15);

    private final String secret;
    private final Clock clock;

    RecyclerAccessTokenIssuer(@Value("${JWT_SECRET}") String secret, Clock clock) {
        this.secret = secret;
        this.clock = clock;
    }

    /**
     * @param currentConsentVersion the version in force now; {@code cv} is present only if the account
     *     accepted exactly that one
     */
    public String issue(RecyclerAccount account, UUID associationId, UUID deviceId, int currentConsentVersion) {
        try {
            Instant now = clock.instant();
            JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                    .subject(account.id().toString())
                    .claim("role", "RECYCLER")
                    .claim("rid", account.recyclerId().toString())
                    .claim("aid", associationId.toString())
                    .claim("did", deviceId.toString())
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(now.plus(LIFETIME)));
            if (hasCurrentConsent(account, currentConsentVersion)) {
                claims.claim("cv", account.consentVersion());
                if (account.locationConsent()) {
                    claims.claim("lc", true);
                }
            }
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims.build());
            jwt.sign(new MACSigner(secret.getBytes(StandardCharsets.UTF_8)));
            return jwt.serialize();
        } catch (JOSEException exception) {
            throw new IllegalStateException("No se pudo firmar el token JWT", exception);
        }
    }

    public static boolean hasCurrentConsent(RecyclerAccount account, int currentConsentVersion) {
        return account.consentVersion() != null && account.consentVersion() == currentConsentVersion;
    }
}
