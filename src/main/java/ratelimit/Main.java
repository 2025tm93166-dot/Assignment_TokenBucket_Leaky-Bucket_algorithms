package ratelimit;

import ratelimiter.LeakyBucketRateLimiter;
import ratelimiter.TokenBucketRateLimiter;

public final class Main {
    private static final long CAPACITY = 3;
    private static final long RATE_PER_SECOND = 1;

    private Main() {
    }

    public static void main(String[] args) throws InterruptedException {
        runTokenBucketDemo();
        runLeakyBucketDemo();
    }

    private static void runTokenBucketDemo() throws InterruptedException {
        TokenBucketRateLimiter limiter =
                new TokenBucketRateLimiter(CAPACITY, RATE_PER_SECOND);

        System.out.println("Token bucket:");
        printRequestResults(limiter::tryAcquire, CAPACITY + 1);
        Thread.sleep(1_100);
        System.out.println("Request after refill: " + result(limiter.tryAcquire()));
        System.out.printf("Available tokens: %.2f%n%n", limiter.getAvailableTokens());
    }

    private static void runLeakyBucketDemo() throws InterruptedException {
        LeakyBucketRateLimiter limiter =
                new LeakyBucketRateLimiter(CAPACITY, RATE_PER_SECOND);

        System.out.println("Leaky bucket:");
        printRequestResults(limiter::tryAcquire, CAPACITY + 1);
        Thread.sleep(1_100);
        System.out.println("Request after leak: " + result(limiter.tryAcquire()));
        System.out.printf("Current load: %.2f%n", limiter.getCurrentLoad());
    }

    private static void printRequestResults(Request request, long count) {
        for (int i = 1; i <= count; i++) {
            System.out.printf("Request %d: %s%n", i, result(request.tryAcquire()));
        }
    }

    private static String result(boolean accepted) {
        return accepted ? "accepted" : "rejected";
    }

    @FunctionalInterface
    private interface Request {
        boolean tryAcquire();
    }
}
