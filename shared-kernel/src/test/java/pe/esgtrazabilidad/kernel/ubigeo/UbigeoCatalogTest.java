package pe.esgtrazabilidad.kernel.ubigeo;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import pe.esgtrazabilidad.kernel.text.TextNormalizer;

import static org.assertj.core.api.Assertions.assertThat;

class UbigeoCatalogTest {

    @Test
    void hasFortyThreeLimaMetropolitanaDistrictsAndSevenCallaoDistricts() {
        assertThat(UbigeoCatalog.all()).hasSize(50);
        assertThat(UbigeoCatalog.all())
                .filteredOn(d -> d.area() == District.Area.LIMA_METROPOLITANA)
                .hasSize(43)
                .allSatisfy(d -> assertThat(d.ubigeo()).startsWith("1501"));
        assertThat(UbigeoCatalog.all())
                .filteredOn(d -> d.area() == District.Area.CALLAO)
                .hasSize(7)
                .allSatisfy(d -> assertThat(d.ubigeo()).startsWith("0701"));
    }

    @Test
    void matchesTheInei2022SourceExtractCodeForCodeAndNameForName() throws IOException {
        // The extract is the 50 rows of INEI's UBIGEOS_2022_1891_distritos.csv for
        // provinces 1501 and 0701, re-encoded from cp1252 to UTF-8, nothing else changed.
        Map<String, String> source = readSourceExtract();

        assertThat(source).hasSize(50);
        assertThat(UbigeoCatalog.all()).allSatisfy(d ->
                assertThat(d.ineiName()).isEqualTo(source.get(d.ubigeo())));
        assertThat(source.get("150105")).isEqualTo("BREÑA");
    }

    @Test
    void displayNamesOnlyAddAccentsAndCaseToTheIneiName() {
        assertThat(UbigeoCatalog.all()).allSatisfy(d ->
                assertThat(TextNormalizer.normalize(d.displayName())).isEqualTo(TextNormalizer.normalize(d.ineiName())));
        assertThat(UbigeoCatalog.find("150128")).get().extracting(District::displayName).isEqualTo("Rímac");
        assertThat(UbigeoCatalog.find("070107")).get().extracting(District::displayName).isEqualTo("Mi Perú");
    }

    @Test
    void findsByCodeAndRefusesCodesOutsideTheList() {
        assertThat(UbigeoCatalog.find("150110")).get().extracting(District::displayName).isEqualTo("Comas");
        assertThat(UbigeoCatalog.contains("150112")).isTrue();
        assertThat(UbigeoCatalog.contains("150201")).isFalse(); // Barranca province, not Lima Metropolitana
        assertThat(UbigeoCatalog.contains("150150")).isFalse(); // SIGERSOL's "-" placeholder row
        assertThat(UbigeoCatalog.contains(null)).isFalse();
    }

    @Test
    void findsByNameIgnoringCaseAccentsAndSpaces() {
        assertThat(UbigeoCatalog.findByName("san martin de porres")).get()
                .extracting(District::ubigeo).isEqualTo("150135");
        assertThat(UbigeoCatalog.findByName("  BREÑA ")).get().extracting(District::ubigeo).isEqualTo("150105");
        assertThat(UbigeoCatalog.findByName("Brena")).get().extracting(District::ubigeo).isEqualTo("150105");
        assertThat(UbigeoCatalog.findByName("Comas Norte")).isEmpty();
        assertThat(UbigeoCatalog.findByName(null)).isEmpty();
    }

    private Map<String, String> readSourceExtract() throws IOException {
        try (InputStream in = getClass().getResourceAsStream("inei-2022-extract-1501-0701.csv")) {
            String[] lines = new String(in.readAllBytes(), StandardCharsets.UTF_8).lines().toArray(String[]::new);
            Map<String, String> byUbigeo = new LinkedHashMap<>();
            for (int i = 1; i < lines.length; i++) {
                String[] fields = lines[i].split(";");
                byUbigeo.put(fields[0], fields[3]);
            }
            return byUbigeo;
        }
    }
}
