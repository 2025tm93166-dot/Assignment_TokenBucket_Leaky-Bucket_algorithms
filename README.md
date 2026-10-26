# Java Rate Limiting Algorithms

This project implements two thread-safe rate limiters in Java:

- **Token bucket** - starts with a full bucket. Tokens are replenished
  continuously up to the configured capacity. A request is accepted when enough
  tokens are available.
- **Leaky bucket** - starts empty. Requests add units to the bucket while it
  drains continuously at the configured rate. A request is accepted when it
  fits within the bucket capacity.

Both limiters support single-request and weighted-permit acquisition. They use
`System.nanoTime()` by default and expose the current token count or bucket load.

## Prerequisites

- JDK 11 or later, with `java` and `javac` available on `PATH`
- Apache Maven installed and available as `mvn`

JUnit 5 is used by the test suite and is downloaded by Maven when needed.

## Project layout

```text
src/
|-- main/java/
|   |-- ratelimit/Main.java
|   `-- ratelimiter/
|       |-- LeakyBucketRateLimiter.java
|       `-- TokenBucketRateLimiter.java
`-- test/java/
    |-- ratelimit/MainTest.java
    `-- ratelimiter/
        |-- LeakyBucketRateLimiterTest.java
        `-- TokenBucketRateLimiterTest.java
pom.xml
```

## Compile

From the project root, compile the application:

```powershell
mvn compile
```

To compile and package the application into a JAR:

```powershell
mvn package
```

## Run the demo

After compiling, run the main class:

```powershell
java -cp target\classes ratelimit.Main
```

The demo runs both algorithms, prints accepted and rejected requests, then
waits briefly to show a request being accepted after tokens refill or the
bucket drains. The example capacity and rate are configured in `Main`.

## Run tests

Run all JUnit tests, including the limiter behavior and main-class demo tests:

```powershell
mvn test
```

The tests cover bucket initialization, burst limits, weighted requests,
fractional refill/leak, capacity bounds, invalid inputs, concurrent access,
and the demo output.

## Using the limiters

Import the limiter classes from the `ratelimiter` package:

```java
import ratelimiter.LeakyBucketRateLimiter;
import ratelimiter.TokenBucketRateLimiter;
```

Construct a limiter with its capacity and rate per second, then call
`tryAcquire()` for one permit or `tryAcquire(permits)` for a weighted request.
The method returns `true` when admitted and `false` when the request cannot be
served immediately.

```java
TokenBucketRateLimiter tokenBucket = new TokenBucketRateLimiter(10, 2);
boolean admitted = tokenBucket.tryAcquire();
double availableTokens = tokenBucket.getAvailableTokens();

LeakyBucketRateLimiter leakyBucket = new LeakyBucketRateLimiter(10, 2);
boolean accepted = leakyBucket.tryAcquire(3);
double currentLoad = leakyBucket.getCurrentLoad();
```
