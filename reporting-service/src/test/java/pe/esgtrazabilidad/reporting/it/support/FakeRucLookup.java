package pe.esgtrazabilidad.reporting.it.support;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import pe.esgtrazabilidad.kernel.ruc.Ruc;
import pe.esgtrazabilidad.reporting.sunat.RucLookup;
import pe.esgtrazabilidad.reporting.sunat.RucLookupResult;

/**
 * The SUNAT provider in every @SpringBootTest context: no test calls the real
 * service. By default any valid RUC is an ACTIVO / HABIDO company named
 * "Empresa SUNAT <ruc>"; a test overrides one RUC with {@link #answer} (or
 * {@link #unavailableAll}) and counts remote calls with {@link #calls}.
 * Use distinct RUCs per test: answers are cached in the database for 24 h.
 */
public class FakeRucLookup implements RucLookup {

    private final Map<String, RucLookupResult> answers = new ConcurrentHashMap<>();
    private final AtomicInteger calls = new AtomicInteger();
    private volatile boolean unavailableAll;

    @Override
    public RucLookupResult lookup(Ruc ruc) {
        calls.incrementAndGet();
        if (unavailableAll) {
            return new RucLookupResult.Unavailable();
        }
        return answers.getOrDefault(
                ruc.value(),
                new RucLookupResult.Found("Empresa SUNAT " + ruc.value(), "ACTIVO", "HABIDO", "150101"));
    }

    public void answer(String ruc, RucLookupResult result) {
        answers.put(ruc, result);
    }

    public void unavailableAll(boolean unavailable) {
        this.unavailableAll = unavailable;
    }

    public int calls() {
        return calls.get();
    }

    public void reset() {
        answers.clear();
        unavailableAll = false;
        calls.set(0);
    }
}
