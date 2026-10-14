package pe.esgtrazabilidad.kernel.observability;

/**
 * What the current request has spent in the database, filled by {@link TimedDataSource} and read
 * by {@link RequestTimingFilter}. One holder per request thread; outside a request nothing is
 * accumulated.
 */
final class RequestTimings {

    private static final ThreadLocal<RequestTimings> CURRENT = new ThreadLocal<>();

    private long queryNanos;
    private long poolNanos;
    private int queries;

    static RequestTimings begin() {
        RequestTimings timings = new RequestTimings();
        CURRENT.set(timings);
        return timings;
    }

    static void end() {
        CURRENT.remove();
    }

    static void addQuery(long nanos) {
        RequestTimings timings = CURRENT.get();
        if (timings != null) {
            timings.queryNanos += nanos;
            timings.queries++;
        }
    }

    static void addPoolWait(long nanos) {
        RequestTimings timings = CURRENT.get();
        if (timings != null) {
            timings.poolNanos += nanos;
        }
    }

    long queryMillis() {
        return queryNanos / 1_000_000;
    }

    long poolMillis() {
        return poolNanos / 1_000_000;
    }

    int queries() {
        return queries;
    }
}
