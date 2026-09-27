package pe.esgtrazabilidad.auth.ratelimit;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import io.github.bucket4j.TimeMeter;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LoginRateLimiterTest {

    /** Manually advanced clock, so refill is tested without sleeping. */
    private static final class FakeClock implements TimeMeter {
        private final AtomicLong nanos = new AtomicLong();

        void advance(Duration duration) {
            nanos.addAndGet(duration.toNanos());
        }

        @Override
        public long currentTimeNanos() {
            return nanos.get();
        }

        @Override
        public boolean isWallClockBased() {
            return false;
        }
    }

    private final FakeClock clock = new FakeClock();
    private final LoginRateLimiter limiter = new LoginRateLimiter(clock, 10_000);

    @Test
    void fiveAttemptsInsideAMinuteAreAllowedAndTheSixthIsRefused() {
        for (int attempt = 1; attempt <= 5; attempt++) {
            assertThat(limiter.tryConsume("203.0.113.7").allowed()).as("attempt %d", attempt).isTrue();
        }

        LoginRateLimiter.Decision sixth = limiter.tryConsume("203.0.113.7");

        assertThat(sixth.allowed()).isFalse();
        assertThat(sixth.retryAfterSeconds()).isPositive();
    }

    @Test
    void anAttemptAfterTheBucketRefillsIsAllowedAgain() {
        for (int attempt = 1; attempt <= 5; attempt++) {
            limiter.tryConsume("203.0.113.7");
        }
        LoginRateLimiter.Decision refused = limiter.tryConsume("203.0.113.7");
        assertThat(refused.allowed()).isFalse();

        clock.advance(Duration.ofSeconds(refused.retryAfterSeconds()));

        assertThat(limiter.tryConsume("203.0.113.7").allowed()).isTrue();
    }

    @Test
    void retryAfterIsRoundedUpToWholeSecondsAndNeverExceedsAMinute() {
        for (int attempt = 1; attempt <= 5; attempt++) {
            limiter.tryConsume("203.0.113.7");
        }

        long retryAfter = limiter.tryConsume("203.0.113.7").retryAfterSeconds();

        assertThat(retryAfter).isBetween(1L, 60L);
    }

    @Test
    void eachClientKeyHasItsOwnBucket() {
        for (int attempt = 1; attempt <= 5; attempt++) {
            limiter.tryConsume("203.0.113.7");
        }
        assertThat(limiter.tryConsume("203.0.113.7").allowed()).isFalse();

        assertThat(limiter.tryConsume("198.51.100.9").allowed()).isTrue();
    }

    @Test
    void theKeyStoreIsBoundedAndEvictsTheLeastRecentlyUsedKeyInsteadOfGrowing() {
        LoginRateLimiter tiny = new LoginRateLimiter(clock, 2);
        for (int attempt = 1; attempt <= 5; attempt++) {
            tiny.tryConsume("a");
        }
        assertThat(tiny.tryConsume("a").allowed()).isFalse();

        tiny.tryConsume("b");
        tiny.tryConsume("a"); // touch "a" again: now "b" is the least recently used
        tiny.tryConsume("c"); // third key: evicts "b" (insertion order would evict "a")

        assertThat(tiny.trackedKeys()).isEqualTo(2);
        // "a" survived with its exhausted bucket; "b" was evicted.
        assertThat(tiny.tryConsume("a").allowed()).isFalse();
    }
}
