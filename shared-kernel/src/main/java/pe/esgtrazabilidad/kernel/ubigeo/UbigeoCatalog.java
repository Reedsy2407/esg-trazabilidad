package pe.esgtrazabilidad.kernel.ubigeo;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import pe.esgtrazabilidad.kernel.text.TextNormalizer;

/**
 * The closed list of districts a neighbour can live in: the 43 of Lima
 * Metropolitana (province 1501) and the 7 of Callao (province 0701), loaded
 * once from {@code lima-callao-districts.csv}, which records its INEI source.
 */
public final class UbigeoCatalog {

    private static final String RESOURCE = "/pe/esgtrazabilidad/kernel/ubigeo/lima-callao-districts.csv";

    private static final Map<String, District> BY_UBIGEO = load();
    private static final Map<String, District> BY_NORMALIZED_NAME = indexByNormalizedName(BY_UBIGEO);

    private UbigeoCatalog() {
    }

    /** All 50 districts, in ubigeo order. */
    public static List<District> all() {
        return List.copyOf(BY_UBIGEO.values());
    }

    public static Optional<District> find(String ubigeo) {
        return Optional.ofNullable(ubigeo == null ? null : BY_UBIGEO.get(ubigeo));
    }

    public static boolean contains(String ubigeo) {
        return find(ubigeo).isPresent();
    }

    /**
     * Matches a district name regardless of case, accents or extra spaces
     * ("RIMAC", "Rímac"). Used to backfill free-text districts and to
     * cross-check SIGERSOL's names; never to choose what to display.
     */
    public static Optional<District> findByName(String name) {
        return Optional.ofNullable(name == null ? null : BY_NORMALIZED_NAME.get(TextNormalizer.normalize(name)));
    }

    private static Map<String, District> load() {
        Map<String, District> districts = new LinkedHashMap<>();
        try (InputStream in = UbigeoCatalog.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Missing classpath resource " + RESOURCE);
            }
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            boolean headerSeen = false;
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                if (!headerSeen) {
                    headerSeen = true;
                    continue;
                }
                String[] fields = line.split(";", -1);
                District district = new District(
                        fields[0], District.Area.valueOf(fields[1]), fields[2], fields[3]);
                if (districts.put(district.ubigeo(), district) != null) {
                    throw new IllegalStateException("Duplicate ubigeo " + district.ubigeo());
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return Collections.unmodifiableMap(districts);
    }

    private static Map<String, District> indexByNormalizedName(Map<String, District> byUbigeo) {
        Map<String, District> index = new LinkedHashMap<>();
        List<String> clashes = new ArrayList<>();
        for (District district : byUbigeo.values()) {
            if (index.put(TextNormalizer.normalize(district.ineiName()), district) != null) {
                clashes.add(district.ineiName());
            }
        }
        if (!clashes.isEmpty()) {
            throw new IllegalStateException("District names that normalize to the same text: " + clashes);
        }
        return Collections.unmodifiableMap(index);
    }
}
