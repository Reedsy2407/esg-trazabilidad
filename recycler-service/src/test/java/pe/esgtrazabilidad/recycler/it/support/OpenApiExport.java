package pe.esgtrazabilidad.recycler.it.support;

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

/**
 * Turns a raw /v3/api-docs response into the exact bytes the portal commits
 * under Writerside/specs/: keys sorted recursively (so two exports of the
 * same API never disagree on field order), a fixed LF/2-space pretty printer
 * (so a Windows checkout and the Linux CI runner produce byte-identical
 * output), and no "servers" entry (springdoc fills that from this test's own
 * random port, which is never the same twice).
 */
public final class OpenApiExport {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private OpenApiExport() {
    }

    public static String normalize(String rawJson) {
        try {
            JsonNode root = MAPPER.readTree(rawJson);
            if (root instanceof ObjectNode objectNode) {
                objectNode.remove("servers");
            }
            JsonNode sorted = sortKeys(root);
            DefaultPrettyPrinter printer = new DefaultPrettyPrinter();
            printer.indentObjectsWith(new DefaultIndenter("  ", "\n"));
            printer.indentArraysWith(new DefaultIndenter("  ", "\n"));
            return MAPPER.writer(printer).writeValueAsString(sorted) + "\n";
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static void writeTo(Path file, String normalizedJson) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, normalizedJson, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static JsonNode sortKeys(JsonNode node) {
        if (node instanceof ObjectNode objectNode) {
            Map<String, JsonNode> sortedFields = new TreeMap<>();
            Iterator<Map.Entry<String, JsonNode>> fields = objectNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                sortedFields.put(field.getKey(), sortKeys(field.getValue()));
            }
            ObjectNode sorted = MAPPER.createObjectNode();
            sortedFields.forEach(sorted::set);
            return sorted;
        }
        if (node instanceof ArrayNode arrayNode) {
            ArrayNode sorted = MAPPER.createArrayNode();
            arrayNode.forEach(element -> sorted.add(sortKeys(element)));
            return sorted;
        }
        return node;
    }
}
