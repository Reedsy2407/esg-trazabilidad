package pe.esgtrazabilidad.kernel.security;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CurrentActorTest {

    @Test
    void takesTheIdFromSubAndTheEmailFromItsClaim() {
        UUID staffUserId = UUID.randomUUID();
        Jwt jwt = jwt(staffUserId.toString(), "ana@example.org");

        assertThat(CurrentActor.from(jwt)).isEqualTo(new CurrentActor(staffUserId, "ana@example.org"));
    }

    @Test
    void keepsAMissingEmailAsNull() {
        UUID staffUserId = UUID.randomUUID();
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "HS256").subject(staffUserId.toString())
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();

        assertThat(CurrentActor.from(jwt).email()).isNull();
    }

    @Test
    void refusesARequestWithoutAToken() {
        assertThatThrownBy(() -> CurrentActor.from(null)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void refusesASubjectThatIsNotAStaffUserId() {
        assertThatThrownBy(() -> CurrentActor.from(jwt("not-a-uuid", "ana@example.org")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static Jwt jwt(String subject, String email) {
        return Jwt.withTokenValue("t").header("alg", "HS256").subject(subject).claim("email", email)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
    }
}
