package pe.esgtrazabilidad.auth.warmup;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import pe.esgtrazabilidad.auth.staffuser.port.out.StaffUserRepository;
import pe.esgtrazabilidad.kernel.warmup.WarmupTask;

/**
 * A login's costs, once: the whole request path (a POST to this service's own /auth/login with an
 * address that has no account: body binding, validation, the rate-limit filter, the 401 body), the
 * e-mail lookup (Hibernate's first query) and one bcrypt comparison against a fixed hash of a
 * throwaway string, so the first real login does not also pay for loading and warming up the hashing
 * code. No account, credential or user data is involved.
 */
@Component
class AuthWarmupTask implements WarmupTask {

    /** bcrypt of a string used nowhere: it opens no account. */
    private static final String THROWAWAY_HASH = "$2a$10$9VAo/3.Q3uy8KB.nPSyvWeWUE.29ryXYV/0uQzLnhsV4nwBLXXCdG";

    private final StaffUserRepository staffUsers;
    private final PasswordEncoder passwordEncoder;
    private final Environment environment;

    AuthWarmupTask(StaffUserRepository staffUsers, PasswordEncoder passwordEncoder, Environment environment) {
        this.staffUsers = staffUsers;
        this.passwordEncoder = passwordEncoder;
        this.environment = environment;
    }

    @Override
    public String name() {
        return "login-path";
    }

    @Override
    public void run() throws Exception {
        String port = environment.getProperty("local.server.port", environment.getProperty("server.port", "8084"));
        HttpClient.newHttpClient()
                .send(
                        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/auth/login"))
                                .timeout(Duration.ofSeconds(30))
                                .header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString(
                                        "{\"email\":\"warmup@invalid.local\",\"password\":\"not-the-password\"}"))
                                .build(),
                        HttpResponse.BodyHandlers.discarding());
        staffUsers.findByEmail("warmup@invalid.local");
        passwordEncoder.matches("not-the-password", THROWAWAY_HASH);
    }
}
