package pe.esgtrazabilidad.kernel.security;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtSecurityConfigTest {

    // >= 32 bytes: Nimbus's MACSigner/NimbusJwtDecoder both reject shorter
    // keys for HS256.
    private static final String SECRET = "test-secret-at-least-32-bytes-long-for-hs256!!";

    private final JwtSecurityConfig config = new JwtSecurityConfig();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void jwtDecoderFailsFastWhenJwtSecretIsMissing() {
        MockEnvironment environment = new MockEnvironment();

        assertThatThrownBy(() -> config.jwtDecoder(environment)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void jwtDecoderDecodesATokenSignedWithTheSameSecret() throws Exception {
        MockEnvironment environment = new MockEnvironment().withProperty("JWT_SECRET", SECRET);
        JwtDecoder decoder = config.jwtDecoder(environment);

        String token = signToken(SECRET, "staff-id-123");

        Jwt jwt = decoder.decode(token);

        assertThat(jwt.getSubject()).isEqualTo("staff-id-123");
    }

    @Test
    void jwtDecoderRejectsATokenSignedWithADifferentSecret() throws Exception {
        MockEnvironment environment = new MockEnvironment().withProperty("JWT_SECRET", SECRET);
        JwtDecoder decoder = config.jwtDecoder(environment);

        String tokenSignedWithWrongSecret = signToken("a-completely-different-secret-value-here-1234", "someone");

        assertThatThrownBy(() -> decoder.decode(tokenSignedWithWrongSecret)).isInstanceOf(JwtException.class);
    }

    @Test
    void authenticationEntryPointWrites401WithTheExpectedJsonShape() throws Exception {
        AuthenticationEntryPoint entryPoint = config.jwtAuthenticationEntryPoint(objectMapper);
        MockHttpServletResponse response = new MockHttpServletResponse();

        entryPoint.commence(null, response, new BadCredentialsException("no token"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentType()).startsWith("application/problem+json");
        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(body.get("code").asText()).isEqualTo("AUTH-000");
        assertThat(body.get("status").asInt()).isEqualTo(401);
        assertThat(body.get("detail").asText()).isNotBlank();
    }

    @Test
    void accessDeniedHandlerWrites403WithTheExpectedJsonShape() throws Exception {
        AccessDeniedHandler handler = config.jwtAccessDeniedHandler(objectMapper);
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.handle(null, response, new AccessDeniedException("denied"));

        assertThat(response.getStatus()).isEqualTo(403);
        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(body.get("code").asText()).isEqualTo("AUTH-000");
        assertThat(body.get("status").asInt()).isEqualTo(403);
    }

    private String signToken(String secret, String subject) throws Exception {
        MACSigner signer = new MACSigner(secret.getBytes(StandardCharsets.UTF_8));
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(subject)
                .expirationTime(new Date(System.currentTimeMillis() + 60_000))
                .build();
        SignedJWT signedJwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        signedJwt.sign(signer);
        return signedJwt.serialize();
    }
}
