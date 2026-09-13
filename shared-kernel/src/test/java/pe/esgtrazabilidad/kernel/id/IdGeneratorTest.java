package pe.esgtrazabilidad.kernel.id;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IdGeneratorTest {

    @Test
    void generatesUuidsWithVersion7AndRfc4122Variant() {
        UUID id = IdGenerator.generate();

        assertThat(id.version()).isEqualTo(7);
        assertThat(id.variant()).isEqualTo(2);
    }

    @Test
    void generatesTimeOrderedUuidsAcrossSuccessiveCalls() throws InterruptedException {
        List<UUID> generated = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            generated.add(IdGenerator.generate());
            Thread.sleep(2);
        }

        List<UUID> sortedByValue = new ArrayList<>(generated);
        Collections.sort(sortedByValue);

        assertThat(generated).isEqualTo(sortedByValue);
    }
}
