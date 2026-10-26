package ratelimiter;

import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * A thread-safe leaky-bucket admission rate limiter.
 *
 * <p>Requests add units to an initially empty bucket. The bucket drains
 * continuously at the configured rate. A request is rejected when adding it
 * would exceed the bucket capacity.</p>
 */
public final class LeakyBucketRateLimiter {
    private static final double NANOS_PER_SECOND = 1_000_000_000.0;

    private final long capacity;
    private final double leakPerNano;
    private final LongSupplier ticker;

    private double waterLevel;
    private long lastLeakNanos;

    /**
     * Creates a limiter that accepts bursts up to {@code capacity}, draining
     * at {@code leaksPerSecond}.
     */
    public LeakyBucketRateLimiter(long capacity, long leaksPerSecond) {
        this(capacity, leaksPerSecond, System::nanoTime);
    }

    LeakyBucketRateLimiter(long capacity, long leaksPerSecond, LongSupplier ticker) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be greater than zero");
        }
        if (leaksPerSecond <= 0) {
            throw new IllegalArgumentException("leaksPerSecond must be greater than zero");
        }

        this.capacity = capacity;
        this.leakPerNano = leaksPerSecond / NANOS_PER_SECOND;
        this.ticker = Objects.requireNonNull(ticker, "ticker");
        this.lastLeakNanos = ticker.getAsLong();
    }

    /** Attempts to admit one request into the bucket. */
    public synchronized boolean tryAcquire() {
        return tryAcquire(1);
    }

    /**
     * Attempts to admit the requested number of units into the bucket.
     *
     * @param permits number of units to add; must be positive and no greater
     *                than the bucket capacity
     * @return {@code true} if the units fit in the bucket
     */
    public synchronized boolean tryAcquire(int permits) {
        validatePermits(permits);
        leak();

        if (waterLevel + permits > capacity) {
            return false;
        }

        waterLevel += permits;
        return true;
    }

    /** Returns the current bucket occupancy after applying any pending leak. */
    public synchronized double getCurrentLoad() {
        leak();
        return waterLevel;
    }

    private void leak() {
        long now = ticker.getAsLong();
        long elapsedNanos = now - lastLeakNanos;
        lastLeakNanos = now;

        if (elapsedNanos > 0 && waterLevel > 0) {
            waterLevel = Math.max(0, waterLevel - elapsedNanos * leakPerNano);
        }
    }

    private void validatePermits(int permits) {
        if (permits <= 0 || permits > capacity) {
            throw new IllegalArgumentException(
                    "permits must be between 1 and the bucket capacity");
        }
    }
}
