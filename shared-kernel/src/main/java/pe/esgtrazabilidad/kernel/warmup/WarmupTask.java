package pe.esgtrazabilidad.kernel.warmup;

/**
 * Work a service does once, right after it starts answering, so the first real user does not pay
 * for it: class loading, Hibernate's first query, the connection pool's first connection, the JIT.
 * Must be read-only, touch no personal data and never depend on anyone being signed in.
 */
public interface WarmupTask {

    /** Short stable name for the log line. */
    String name();

    void run() throws Exception;
}
