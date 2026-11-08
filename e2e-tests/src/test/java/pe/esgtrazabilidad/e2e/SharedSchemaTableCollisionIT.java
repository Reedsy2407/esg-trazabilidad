package pe.esgtrazabilidad.e2e;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * In production the four services share ONE database and ONE schema (the DB_URL of the
 * esg-shared group), but every IT runs against its own isolated container, so none of them
 * can see two services creating the same table. This reads the Liquibase changelogs of all
 * four and fails if a table is created by more than one service. The fix for a clash is the
 * service suffix (outbox_event_recycler, recycler_status_copy_auth).
 */
class SharedSchemaTableCollisionIT {

    private static final List<String> SERVICES =
            List.of("auth-service", "collection-service", "recycler-service", "reporting-service");

    private static final Pattern CREATE_TABLE_YAML =
            Pattern.compile("createTable:\\s*\\R\\s*(?:#.*\\R\\s*)*(?:schemaName:.*\\R\\s*)?tableName:\\s*[\"']?(\\w+)");
    private static final Pattern CREATE_TABLE_SQL =
            Pattern.compile("(?i)create\\s+table\\s+(?:if\\s+not\\s+exists\\s+)?(?:public\\.)?(\\w+)");

    @Test
    void noTableIsCreatedByTwoDifferentServices() throws IOException {
        Path root = Path.of(System.getProperty("e2e.repo.root")).toAbsolutePath().normalize();
        Map<String, Set<String>> servicesByTable = new TreeMap<>();

        for (String service : SERVICES) {
            Path changelogs = root.resolve(service).resolve("src/main/resources/db/changelog");
            assertThat(changelogs).as("changelogs of " + service).isDirectory();
            for (String table : tablesCreatedIn(changelogs)) {
                servicesByTable.computeIfAbsent(table.toLowerCase(), k -> new TreeSet<>()).add(service);
            }
        }

        assertThat(servicesByTable).as("sanity: the scan found the tables").hasSizeGreaterThan(10);
        Map<String, Set<String>> clashes = new TreeMap<>();
        servicesByTable.forEach((table, services) -> {
            if (services.size() > 1) {
                clashes.put(table, services);
            }
        });
        assertThat(clashes)
                .as("tables created by more than one service (they share one schema in production; add the service suffix)")
                .isEmpty();
    }

    private static Set<String> tablesCreatedIn(Path dir) throws IOException {
        Set<String> tables = new TreeSet<>();
        try (Stream<Path> files = Files.walk(dir)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                String name = file.getFileName().toString();
                if (!(name.endsWith(".yaml") || name.endsWith(".yml") || name.endsWith(".sql"))) {
                    continue;
                }
                String content = Files.readString(file);
                collect(CREATE_TABLE_YAML.matcher(content), tables);
                collect(CREATE_TABLE_SQL.matcher(content), tables);
            }
        }
        return tables;
    }

    private static void collect(Matcher matcher, Set<String> into) {
        while (matcher.find()) {
            into.add(matcher.group(1));
        }
    }
}
