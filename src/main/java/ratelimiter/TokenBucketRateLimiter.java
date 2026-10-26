package ratelimiter;

import java.util.Objects;
import java.util.function.LongSupplier;

/**
 * A thread-safe token-bucket rate limiter.
 *
 * <p>The bucket starts full. Tokens are replenished continuously at the configured
 * rate, up to the bucket capacity. An acquisition succeeds only when enough
 * tokens are available.</p>
 */
public final class TokenBucketRateLimiter {
    private static final double NANOS_PER_SECOND = 1_000_000_000.0;

    private final long capacity;
    private final double tokensPerNano;
    private final LongSupplier ticker;

    private double tokens;
    private long lastRefillNanos;

    /**
     * Creates a limiter that permits bursts up to {@code capacity}, replenishing
     * at {@code tokensPerSecond}.
     */
    public TokenBucketRateLimiter(long capacity, long tokensPerSecond) {
        this(capacity, tokensPerSecond, System::nanoTime);
    }

    TokenBucketRateLimiter(long capacity, long tokensPerSecond, LongSupplier ticker) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be greater than zero");
        }
        if (tokensPerSecond <= 0) {
            throw new IllegalArgumentException("tokensPerSecond must be greater than zero");
        }

        this.capacity = capacity;
        this.tokensPerNano = tokensPerSecond / NANOS_PER_SECOND;
        this.ticker = Objects.requireNonNull(ticker, "ticker");
        this.tokens = capacity;
        this.lastRefillNanos = ticker.getAsLong();
    }

    /** Attempts to acquire one token. */
    public synchronized boolean tryAcquire() {
        return tryAcquire(1);
    }

    /**
     * Attempts to acquire the requested number of tokens.
     *
     * @param permits number of tokens to acquire; must be positive and no greater
     *                than the bucket capacity
     * @return {@code true} if the tokens were acquired
     */
    public synchronized boolean tryAcquire(int permits) {
        validatePermits(permits);
        refill();

        if (tokens < permits) {
            return false;
        }

        tokens -= permits;
        return true;
    }

    /** Returns the current token count after applying any pending refill. */
    public synchronized double getAvailableTokens() {
        refill();
        return tokens;
    }

    private void refill() {
        long now = ticker.getAsLong();
        long elapsedNanos = now - lastRefillNanos;
        lastRefillNanos = now;

        if (elapsedNanos > 0 && tokens < capacity) {
            tokens = Math.min(capacity, tokens + elapsedNanos * tokensPerNano);
        }
    }

    private void validatePermits(int permits) {
        if (permits <= 0 || permits > capacity) {
            throw new IllegalArgumentException(
                    "permits must be between 1 and the bucket capacity");
        }
    }
}
