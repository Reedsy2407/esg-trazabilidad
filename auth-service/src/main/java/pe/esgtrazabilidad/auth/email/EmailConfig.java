package pe.esgtrazabilidad.auth.email;

import java.net.URI;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Chooses the email provider, like RucLookupConfig does for SUNAT. {@code EMAIL_PROVIDER} has no
 * default, so the service does not start without it. {@code fake} and {@code none} are for
 * development and tests: they are refused when {@code RENDER=true} and password recovery is switched
 * on ({@code PASSWORD_RESET_EMAIL_ENABLED=true}), because recovery would then promise an email that
 * never leaves. {@code appsscript} needs the web app's /exec URL and the shared secret; neither has a
 * default.
 */
@Configuration
class EmailConfig {

    static final String WEBHOOK_PREFIX = "https://script.google.com/macros/s/";
    static final int MIN_SECRET_LENGTH = 32;

    @Bean
    EmailSender emailSender(Environment environment) {
        String provider = environment.getRequiredProperty("EMAIL_PROVIDER");
        return switch (provider) {
            case "appsscript" -> appsScript(environment);
            case "fake" -> {
                refuseOnRender(environment, provider);
                yield new FakeEmailSender();
            }
            case "none" -> {
                refuseOnRender(environment, provider);
                yield new UnavailableEmailSender();
            }
            default -> throw new IllegalStateException("EMAIL_PROVIDER must be appsscript, fake or none, got " + provider);
        };
    }

    private static EmailSender appsScript(Environment environment) {
        String url = environment.getProperty("EMAIL_WEBHOOK_URL");
        if (url == null || !url.startsWith(WEBHOOK_PREFIX) || url.length() == WEBHOOK_PREFIX.length()) {
            throw new IllegalStateException("EMAIL_PROVIDER=appsscript needs EMAIL_WEBHOOK_URL starting with " + WEBHOOK_PREFIX);
        }
        String secret = environment.getProperty("EMAIL_WEBHOOK_SECRET");
        if (secret == null || secret.length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException(
                    "EMAIL_PROVIDER=appsscript needs EMAIL_WEBHOOK_SECRET of at least " + MIN_SECRET_LENGTH + " characters");
        }
        return new AppsScriptEmailSender(URI.create(url), secret);
    }

    private static void refuseOnRender(Environment environment, String provider) {
        boolean onRender = "true".equalsIgnoreCase(environment.getProperty("RENDER"));
        boolean recoveryOn = "true".equalsIgnoreCase(environment.getProperty("PASSWORD_RESET_EMAIL_ENABLED"));
        if (onRender && recoveryOn) {
            throw new IllegalStateException(
                    "EMAIL_PROVIDER=" + provider + " is for development and tests only: refused when RENDER=true"
                            + " and PASSWORD_RESET_EMAIL_ENABLED=true");
        }
    }
}
