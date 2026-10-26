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

class LeakyBucketRateLimiterTest {
    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    @Test
    void startsEmptyAndRejectsRequestsWhenFull() {
        LeakyBucketRateLimiter limiter = new LeakyBucketRateLimiter(2, 1, new AtomicLong()::get);

        assertEquals(0.0, limiter.getCurrentLoad(), 0.0);
        assertTrue(limiter.tryAcquire());
        assertTrue(limiter.tryAcquire());
        assertFalse(limiter.tryAcquire());
        assertEquals(2.0, limiter.getCurrentLoad(), 0.0);
    }

    @Test
    void admitsWeightedPermitsUpToCapacity() {
        LeakyBucketRateLimiter limiter =
                new LeakyBucketRateLimiter(5, 1, new AtomicLong()::get);

        assertTrue(limiter.tryAcquire(3));
        assertEquals(3.0, limiter.getCurrentLoad(), 0.0);
        assertTrue(limiter.tryAcquire(2));
        assertFalse(limiter.tryAcquire(1));
    }

    @Test
    void leaksFractionallyAndDoesNotChangeLoadOnRejectedAcquisition() {
        AtomicLong ticker = new AtomicLong();
        LeakyBucketRateLimiter limiter = new LeakyBucketRateLimiter(3, 4, ticker::get);
        assertTrue(limiter.tryAcquire(2));
        assertFalse(limiter.tryAcquire(2));

        ticker.set(125_000_000L);
        assertEquals(1.5, limiter.getCurrentLoad(), 0.000001);
        assertTrue(limiter.tryAcquire(1));
        assertEquals(2.5, limiter.getCurrentLoad(), 0.000001);
    }

    @Test
    void leakNeverReducesLoadBelowZero() {
        AtomicLong ticker = new AtomicLong();
        LeakyBucketRateLimiter limiter = new LeakyBucketRateLimiter(2, 3, ticker::get);
        assertTrue(limiter.tryAcquire());

        ticker.set(10 * NANOS_PER_SECOND);
        assertEquals(0.0, limiter.getCurrentLoad(), 0.0);
        assertTrue(limiter.tryAcquire(2));
    }

    @Test
    void concurrentRequestsCannotExceedBucketCapacity() throws Exception {
        int callers = 12;
        LeakyBucketRateLimiter limiter = new LeakyBucketRateLimiter(1, 1, new AtomicLong()::get);
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
                () -> new LeakyBucketRateLimiter(0, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new LeakyBucketRateLimiter(1, 0));
        assertThrows(NullPointerException.class,
                () -> new LeakyBucketRateLimiter(1, 1, null));

        LeakyBucketRateLimiter limiter =
                new LeakyBucketRateLimiter(2, 1, new AtomicLong()::get);
        assertThrows(IllegalArgumentException.class, () -> limiter.tryAcquire(0));
        assertThrows(IllegalArgumentException.class, () -> limiter.tryAcquire(3));
        assertEquals(0.0, limiter.getCurrentLoad(), 0.0);
    }
}
