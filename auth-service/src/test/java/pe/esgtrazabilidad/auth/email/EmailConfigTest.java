package pe.esgtrazabilidad.auth.email;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailConfigTest {

    private static final String URL = "https://script.google.com/macros/s/AKfycbxEjemplo/exec";
    private static final String SECRET = "un-secreto-compartido-de-prueba-de-32-caracteres";

    private final EmailConfig config = new EmailConfig();

    @Test
    void theProviderHasNoDefault() {
        assertThatThrownBy(() -> config.emailSender(new MockEnvironment())).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void anUnknownProviderIsRefused() {
        assertThatThrownBy(() -> config.emailSender(new MockEnvironment().withProperty("EMAIL_PROVIDER", "resend")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("appsscript, fake or none");
    }

    @Test
    void appsscriptNeedsTheWebAppUrlOnGoogleAndASecretOfAtLeast32Characters() {
        MockEnvironment valid = new MockEnvironment()
                .withProperty("EMAIL_PROVIDER", "appsscript")
                .withProperty("EMAIL_WEBHOOK_URL", URL)
                .withProperty("EMAIL_WEBHOOK_SECRET", SECRET);
        assertThat(config.emailSender(valid)).isInstanceOf(AppsScriptEmailSender.class);

        for (String badUrl : new String[] {
            "", "http://script.google.com/macros/s/AKfy/exec", "https://script.google.com.evil.example/macros/s/x/exec",
            "https://example.com/macros/s/AKfy/exec", "https://script.google.com/macros/s/"
        }) {
            MockEnvironment environment = new MockEnvironment()
                    .withProperty("EMAIL_PROVIDER", "appsscript")
                    .withProperty("EMAIL_WEBHOOK_URL", badUrl)
                    .withProperty("EMAIL_WEBHOOK_SECRET", SECRET);
            assertThatThrownBy(() -> config.emailSender(environment)).as(badUrl).isInstanceOf(IllegalStateException.class);
        }
        assertThatThrownBy(() -> config.emailSender(new MockEnvironment()
                        .withProperty("EMAIL_PROVIDER", "appsscript")
                        .withProperty("EMAIL_WEBHOOK_URL", URL)))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> config.emailSender(new MockEnvironment()
                        .withProperty("EMAIL_PROVIDER", "appsscript")
                        .withProperty("EMAIL_WEBHOOK_URL", URL)
                        .withProperty("EMAIL_WEBHOOK_SECRET", "demasiado-corto")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void fakeAndNoneAreRefusedOnRenderWhileRecoveryIsSwitchedOn() {
        for (String provider : new String[] {"fake", "none"}) {
            MockEnvironment onRender = new MockEnvironment()
                    .withProperty("EMAIL_PROVIDER", provider)
                    .withProperty("RENDER", "true")
                    .withProperty("PASSWORD_RESET_EMAIL_ENABLED", "true");
            assertThatThrownBy(() -> config.emailSender(onRender)).as(provider).isInstanceOf(IllegalStateException.class);

            // Switched off, or off Render, they are fine (development, tests, an emergency stop).
            assertThat(config.emailSender(new MockEnvironment()
                            .withProperty("EMAIL_PROVIDER", provider)
                            .withProperty("RENDER", "true")
                            .withProperty("PASSWORD_RESET_EMAIL_ENABLED", "false")))
                    .isNotNull();
            assertThat(config.emailSender(new MockEnvironment().withProperty("EMAIL_PROVIDER", provider)))
                    .isNotNull();
        }
    }
}
