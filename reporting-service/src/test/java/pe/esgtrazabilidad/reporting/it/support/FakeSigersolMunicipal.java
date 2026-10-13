package pe.esgtrazabilidad.reporting.it.support;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.transaction.support.TransactionSynchronizationManager;

import pe.esgtrazabilidad.reporting.sigersolmunicipal.SigersolMunicipalStats;

/**
 * SIGERSOL Municipal in every @SpringBootTest context: no test calls the real service. By default
 * every district has no rows ({@code NoData}); a test sets a district-year with {@link #answer}, or
 * makes everything unavailable with {@link #unavailableAll}. Counts remote calls with {@link #calls}.
 * Use distinct districts per test: answers are stored in the database as snapshots.
 */
public class FakeSigersolMunicipal implements SigersolMunicipalStats {

    private final Map<String, Answer> answers = new ConcurrentHashMap<>();
    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicInteger callsInsideATransaction = new AtomicInteger();
    private volatile boolean unavailableAll;

    @Override
    public Answer fetch(int year, String ubigeo) {
        calls.incrementAndGet();
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            callsInsideATransaction.incrementAndGet();
        }
        if (unavailableAll) {
            return new Answer.Unavailable();
        }
        return answers.getOrDefault(year + "/" + ubigeo, new Answer.NoData());
    }

    public void answer(int year, String ubigeo, Answer answer) {
        answers.put(year + "/" + ubigeo, answer);
    }

    public void unavailableAll(boolean unavailable) {
        this.unavailableAll = unavailable;
    }

    public int calls() {
        return calls.get();
    }

    /** Remote calls made while a database transaction was open (there must be none). */
    public int callsInsideATransaction() {
        return callsInsideATransaction.get();
    }

    public void reset() {
        answers.clear();
        unavailableAll = false;
        calls.set(0);
        callsInsideATransaction.set(0);
    }
}
