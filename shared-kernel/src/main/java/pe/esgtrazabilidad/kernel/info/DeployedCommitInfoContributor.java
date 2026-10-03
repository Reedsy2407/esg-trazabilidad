package pe.esgtrazabilidad.kernel.info;

import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.boot.actuate.info.Info;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.core.env.Environment;

/**
 * Adds the deployed commit to GET /actuator/info as {"commit": "<sha>"}, so a
 * deploy can be checked against the commit CI passed (scripts/verify-deploy.sh).
 * Render injects RENDER_GIT_COMMIT at build time and runtime. Each service
 * turns every default info contributor off (management.info.defaults.enabled),
 * so this field is the endpoint's whole body. Anything that isn't a hex SHA is
 * ignored rather than echoed; locally, with no variable, the body is {}.
 */
public class DeployedCommitInfoContributor implements InfoContributor {

    static final String COMMIT_VARIABLE = "RENDER_GIT_COMMIT";

    private static final Pattern SHA = Pattern.compile("[0-9a-fA-F]{7,40}");

    private final Environment environment;

    public DeployedCommitInfoContributor(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void contribute(Info.Builder builder) {
        String commit = environment.getProperty(COMMIT_VARIABLE, "").trim();
        if (SHA.matcher(commit).matches()) {
            builder.withDetail("commit", commit.toLowerCase(Locale.ROOT));
        }
    }
}
