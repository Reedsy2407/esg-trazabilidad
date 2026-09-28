package pe.esgtrazabilidad.collection.cdgate;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.fail;

/**
 * Deliberately failing, on the throwaway branch cd-gate-check only: proves
 * Render's checksPass never deploys a commit whose CI is red (Task 84).
 * Never merged; the branch is deleted after the check.
 */
class CdGateCheckTest {

    @Test
    void deliberatelyFailsSoCiIsRed() {
        fail("Deliberate failure for the checksPass negative check (Task 84)");
    }
}
