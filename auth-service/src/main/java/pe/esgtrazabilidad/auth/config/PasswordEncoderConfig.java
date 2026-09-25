package pe.esgtrazabilidad.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Deliberately depends only on spring-security-crypto, not the full
 * spring-boot-starter-security -- pulling in the starter here would trigger
 * Spring Boot's default SecurityAutoConfiguration (HTTP Basic + a generated
 * password on every endpoint) before Task 62 wires the real JWT-based
 * SecurityConfig.
 */
@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
