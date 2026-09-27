package pe.esgtrazabilidad.auth.ratelimit;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.TimeMeter;

/**
 * Per-client token buckets for POST /auth/login: 5 attempts per minute,
 * successful or not, with a burst of 5 (SPEC-deployment.md). The buckets are
 * in memory and per instance: the free tier runs exactly one auth-service
 * instance, and a reset on restart is acceptable. The limit is there against
 * sustained brute force; BCrypt and the no-enumeration AUTH-001 cover the rest.
 *
 * <p>The key store is a bounded LRU map, so a flood of distinct (or spoofed)
 * keys evicts old buckets instead of growing memory inside a 512 MB container.
 */
public class LoginRateLimiter {

    /** The production limit; tests other than the rate-limit ones raise it (see RateLimitConfig). */
    public static final int DEFAULT_CAPACITY = 5;
    static final Duration REFILL_PERIOD = Duration.ofMinutes(1);
    public static final int DEFAULT_MAX_KEYS = 10_000;

    public record Decision(boolean allowed, long retryAfterSeconds) {}

    private final TimeMeter clock;
    private final int capacity;
    private final Map<String, Bucket> buckets;

    public LoginRateLimiter(int capacity) {
        this(TimeMeter.SYSTEM_NANOTIME, capacity, DEFAULT_MAX_KEYS);
    }

    LoginRateLimiter(TimeMeter clock, int maxKeys) {
        this(clock, DEFAULT_CAPACITY, maxKeys);
    }

    private LoginRateLimiter(TimeMeter clock, int capacity, int maxKeys) {
        if (capacity < 1) {
            throw new IllegalArgumentException("capacity must be at least 1");
        }
        this.clock = clock;
        this.capacity = capacity;
        this.buckets = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Bucket> eldest) {
                return size() > maxKeys;
            }
        };
    }

    public Decision tryConsume(String clientKey) {
        Bucket bucket;
        // LinkedHashMap in access order mutates on get(), so every access,
        // not only inserts, has to be serialized.
        synchronized (buckets) {
            bucket = buckets.computeIfAbsent(clientKey, key -> newBucket());
        }
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            return new Decision(true, 0);
        }
        long nanos = probe.getNanosToWaitForRefill();
        long seconds = Math.max(1, (nanos + 999_999_999L) / 1_000_000_000L);
        return new Decision(false, seconds);
    }

    int trackedKeys() {
        synchronized (buckets) {
            return buckets.size();
        }
    }

    private Bucket newBucket() {
        return Bucket.builder()
                .addLimit(limit -> limit.capacity(capacity).refillGreedy(capacity, REFILL_PERIOD))
                .withCustomTimePrecision(clock)
                .build();
    }
}
