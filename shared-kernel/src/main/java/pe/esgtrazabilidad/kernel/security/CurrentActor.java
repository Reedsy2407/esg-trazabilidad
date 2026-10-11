package pe.esgtrazabilidad.kernel.security;

import java.util.UUID;

import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Who is doing a write: the staff user id ({@code sub}) and the {@code email}
 * claim of the token auth-service issued. Controllers build it from
 * {@code @AuthenticationPrincipal Jwt} and pass it down in the command, so
 * every "who" column (annulment, deletion, status change, SIGERSOL record) comes
 * from the validated token and never from a request body. The id identifies
 * the actor; the email is a convenience copy and may be missing from a token,
 * so every "who" email column is nullable.
 */
public record CurrentActor(UUID id, String email) {

    public CurrentActor {
        if (id == null) {
            throw new IllegalArgumentException("The actor id is required");
        }
    }

    public static CurrentActor from(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            throw new IllegalStateException("No authenticated staff user in this request");
        }
        return new CurrentActor(UUID.fromString(jwt.getSubject()), jwt.getClaimAsString("email"));
    }
}
