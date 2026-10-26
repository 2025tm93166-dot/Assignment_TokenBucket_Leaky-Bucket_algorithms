package ratelimiter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class TokenBucketRateLimiterTest {
    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    @Test
    void startsFullAndRejectsRequestsWhenEmpty() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(2, 1, new AtomicLong()::get);

        assertEquals(2.0, limiter.getAvailableTokens(), 0.0);
        assertTrue(limiter.tryAcquire());
        assertTrue(limiter.tryAcquire());
        assertFalse(limiter.tryAcquire());
        assertEquals(0.0, limiter.getAvailableTokens(), 0.0);
    }

    @Test
    void acquiresWeightedPermits() {
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(5, 1, new AtomicLong()::get);

        assertTrue(limiter.tryAcquire(3));
        assertEquals(2.0, limiter.getAvailableTokens(), 0.0);
        assertTrue(limiter.tryAcquire(2));
        assertFalse(limiter.tryAcquire(1));
    }

    @Test
    void refillsFractionallyAndDoesNotConsumeOnFailedAcquisition() {
        AtomicLong ticker = new AtomicLong();
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(3, 4, ticker::get);
        assertTrue(limiter.tryAcquire(2));

        ticker.set(125_000_000L);
        assertFalse(limiter.tryAcquire(2));
        assertEquals(1.5, limiter.getAvailableTokens(), 0.000001);

        ticker.set(250_000_000L);
        assertTrue(limiter.tryAcquire(2));
        assertEquals(0.0, limiter.getAvailableTokens(), 0.000001);
    }

    @Test
    void refillNeverExceedsCapacity() {
        AtomicLong ticker = new AtomicLong();
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(2, 3, ticker::get);

        ticker.set(10 * NANOS_PER_SECOND);
        assertEquals(2.0, limiter.getAvailableTokens(), 0.0);
        assertTrue(limiter.tryAcquire(2));
        assertFalse(limiter.tryAcquire());
    }

    @Test
    void concurrentAcquisitionsCannotOverspendTokens() throws Exception {
        int callers = 12;
        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(1, 1, new AtomicLong()::get);
        ExecutorService executor = Executors.newFixedThreadPool(callers);
        CountDownLatch ready = new CountDownLatch(callers);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();

        try {
            for (int i = 0; i < callers; i++) {
                results.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return limiter.tryAcquire();
                }));
            }

            ready.await();
            start.countDown();

            int accepted = 0;
            for (Future<Boolean> result : results) {
                if (result.get()) {
                    accepted++;
                }
            }
            assertEquals(1, accepted);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void rejectsInvalidConfigurationAndRequests() {
        assertThrows(IllegalArgumentException.class,
                () -> new TokenBucketRateLimiter(0, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new TokenBucketRateLimiter(1, 0));
        assertThrows(NullPointerException.class,
                () -> new TokenBucketRateLimiter(1, 1, null));

        TokenBucketRateLimiter limiter = new TokenBucketRateLimiter(2, 1, new AtomicLong()::get);
        assertThrows(IllegalArgumentException.class, () -> limiter.tryAcquire(0));
        assertThrows(IllegalArgumentException.class, () -> limiter.tryAcquire(3));
        assertEquals(2.0, limiter.getAvailableTokens(), 0.0);
    }
}
