package pe.esgtrazabilidad.kernel.warmup;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;

/**
 * After the service is ready, on its own thread (readiness never waits for it): one request to the
 * service's own public {@code /actuator/health} (the whole filter chain, JSON, and a first database
 * connection, which also wakes a suspended Neon compute) and then every {@link WarmupTask}.
 * Each step logs {@code event=warmup service=… step=… durationMs=…} at INFO, or at ERROR when it
 * failed; a failure never stops the service. Off unless {@code esg.warmup.enabled=true} (the Docker
 * images turn it on), so tests and local runs are untouched.
 */
public class StartupWarmup {

    private static final Logger log = LoggerFactory.getLogger("esg.warmup");

    private final Environment environment;
    private final List<WarmupTask> tasks;

    public StartupWarmup(Environment environment, List<WarmupTask> tasks) {
        this.environment = environment;
        this.tasks = tasks;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        Thread thread = new Thread(this::warm, "startup-warmup");
        thread.setDaemon(true);
        thread.start();
    }

    private void warm() {
        String service = environment.getProperty("spring.application.name", "unknown");
        String port = environment.getProperty("local.server.port", environment.getProperty("server.port", "8080"));
        step(service, "health", () -> {
            HttpResponse<Void> response = HttpClient.newHttpClient()
                    .send(
                            HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/actuator/health"))
                                    .timeout(Duration.ofSeconds(30))
                                    .GET()
                                    .build(),
                            HttpResponse.BodyHandlers.discarding());
            // 503 (a provider is down) still ran the whole path; only an exception is a failure.
            return response.statusCode();
        });
        // A bearer token that is not a real one: the JWT decoder, the security chain and the 401 body
        // load and run once. It authenticates nobody and reaches no data.
        step(service, "security-chain", () -> HttpClient.newHttpClient()
                .send(
                        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/warmup-probe"))
                                .timeout(Duration.ofSeconds(30))
                                .header("Authorization", "Bearer warmup.probe.invalid")
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.discarding())
                .statusCode());
        for (WarmupTask task : tasks) {
            step(service, task.name(), () -> {
                task.run();
                return 0;
            });
        }
    }

    private interface Step {
        int run() throws Exception;
    }

    private void step(String service, String name, Step step) {
        long start = System.nanoTime();
        try {
            step.run();
            log.info("event=warmup service={} step={} durationMs={}", service, name, (System.nanoTime() - start) / 1_000_000);
        } catch (Exception e) {
            log.error(
                    "event=warmup service={} step={} durationMs={} failed={}",
                    service,
                    name,
                    (System.nanoTime() - start) / 1_000_000,
                    e.getClass().getSimpleName());
        }
    }
}
