package pe.esgtrazabilidad.e2e.support;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One real, separately running service: its own packaged -exec.jar launched
 * with `java -jar` in its own OS process, configured only through environment
 * variables -- the same way it runs when deployed. Nothing is shared in-memory
 * with the test JVM or with the other services.
 *
 * Each process runs from its own empty working directory, so the services'
 * `spring.config.import: optional:file:../.env.local` can never pick up a
 * developer's real `.env.local`. The child inherits the test JVM's environment
 * (Windows needs e.g. SystemRoot for sockets), minus anything that could
 * configure a service behind the test's back: every `SPRING_*` variable and
 * every key the services read from `.env.local`. Only values this test sets
 * explicitly reach the service for those.
 */
public final class ServiceProcess {

    private static final Pattern STARTED = Pattern.compile("Started \\w+ in [0-9.]+ seconds");

    // Keys the services read (see each application.yml); never inherited from
    // the developer's shell, only set by the test.
    private static final Set<String> SERVICE_CONFIG_KEYS = Set.of(
            "DB_URL", "DB_USERNAME", "DB_PASSWORD", "POSTGRES_PASSWORD",
            "RABBITMQ_HOST", "RABBITMQ_PORT", "RABBITMQ_USER", "RABBITMQ_PASSWORD",
            "JWT_SECRET", "ADMIN_BOOTSTRAP_EMAIL", "SERVER_PORT");

    private final String name;
    private final int port;
    private final Path logFile;
    private final Process process;

    private ServiceProcess(String name, int port, Path logFile, Process process) {
        this.name = name;
        this.port = port;
        this.logFile = logFile;
        this.process = process;
    }

    public static ServiceProcess launch(String name, Map<String, String> env) throws IOException {
        Path jar = Path.of(System.getProperty("e2e.repo.root"), name, "target", name + "-exec.jar")
                .toAbsolutePath()
                .normalize();
        if (!Files.isRegularFile(jar)) {
            throw new IllegalStateException(
                    jar + " not found -- build the services first (`mvn verify` on the whole reactor, or `-pl e2e-tests -am`)");
        }

        Path workDir = Path.of(System.getProperty("e2e.logs.dir"), name);
        Files.createDirectories(workDir);
        Path logFile = workDir.resolve(name + ".log");

        int port = freePort();
        String java = ProcessHandle.current().info().command().orElse("java");
        ProcessBuilder builder = new ProcessBuilder(java, "-jar", jar.toString())
                .directory(workDir.toFile())
                .redirectErrorStream(true)
                .redirectOutput(logFile.toFile());
        builder.environment().keySet().removeIf(key -> key.startsWith("SPRING_") || SERVICE_CONFIG_KEYS.contains(key));
        builder.environment().putAll(env);
        builder.environment().put("SERVER_PORT", String.valueOf(port));

        Process process = builder.start();
        // If the test JVM itself dies (Ctrl+C, CI timeout) @AfterAll never runs;
        // without this the service would outlive it and keep holding its port.
        Runtime.getRuntime().addShutdownHook(new Thread(process::destroyForcibly));
        return new ServiceProcess(name, port, logFile, process);
    }

    /** Blocks until Spring Boot logs its "Started ... in N seconds" line. */
    public void awaitStarted(Duration timeout) throws IOException, InterruptedException {
        Instant deadline = Instant.now().plus(timeout);
        while (Instant.now().isBefore(deadline)) {
            if (STARTED.matcher(log()).find()) {
                return;
            }
            if (!process.isAlive()) {
                throw new IllegalStateException(name + " exited with code " + process.exitValue() + " before starting:\n" + logTail());
            }
            Thread.sleep(250);
        }
        throw new IllegalStateException(name + " did not start within " + timeout + ":\n" + logTail());
    }

    /** The first match of {@code regex} in this service's log so far; fails if there is none. */
    public Matcher findInLog(String regex) throws IOException {
        Matcher matcher = Pattern.compile(regex).matcher(log());
        if (!matcher.find()) {
            throw new IllegalStateException(name + " log has no line matching " + regex + ":\n" + logTail());
        }
        return matcher;
    }

    public void stop() throws InterruptedException {
        process.destroy();
        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroyForcibly().waitFor(30, TimeUnit.SECONDS);
        }
    }

    public boolean isAlive() {
        return process.isAlive();
    }

    public int port() {
        return port;
    }

    public String name() {
        return name;
    }

    private String log() throws IOException {
        // ISO-8859-1 never fails to decode, whatever console encoding the
        // child JVM picked; everything this test matches on is plain ASCII.
        return Files.exists(logFile) ? Files.readString(logFile, StandardCharsets.ISO_8859_1) : "";
    }

    private String logTail() throws IOException {
        String log = log();
        return log.length() <= 4000 ? log : log.substring(log.length() - 4000);
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
