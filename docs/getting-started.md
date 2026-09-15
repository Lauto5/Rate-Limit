# Getting Started

This guide takes you from zero to a working rate limiter in the fewest possible steps: one
algorithm, one persistence adapter, no advanced options. It is intentionally minimal.
For the rationale behind the API, see [architecture.md](architecture.md).

## Prerequisites

- **Maven** 3.9+ (or Gradle) and an installed JDK 17+ to build.
- The examples in this guide run on Java 8+ when using the in-memory/Redis modules.

Add the dependencies to your `pom.xml`:

```xml
<dependencies>
    <dependency>
        <groupId>io.github.lauto5</groupId>
        <artifactId>rate-limit-core</artifactId>
        <version>1.0.0</version>
    </dependency>
    <dependency>
        <groupId>io.github.lauto5</groupId>
        <artifactId>rate-limit-inmemory</artifactId>
        <version>1.0.0</version>
    </dependency>
</dependencies>
```

`rate-limit-core` brings the API and algorithms. `rate-limit-inmemory` gives you a store that
keeps state inside the JVM process — enough for a first run. (For shared, distributed limits,
use [`rate-limit-redis`](../persistence/redis.md) instead.)

## 1. Build a rate limiter

```java
import io.github.lauto5.rateLimit.RateLimit;
import io.github.lauto5.rateLimit.api.Algorithm;
import io.github.lauto5.rateLimit.api.RateLimitResult;
import io.github.lauto5.rateLimit.domain.policies.FixedWindowPolicy;
import io.github.lauto5.rateLimit.inmemory.Persistence;

import java.time.Duration;

RateLimit<FixedWindowPolicy> rateLimit = RateLimit.build(
        Algorithm.fixedWindow(),     // the algorithm that evaluates requests
        Persistence.inMemory()       // the store that holds the state
);
```

`Algorithm.fixedWindow()` returns an algorithm that only works with `FixedWindowPolicy` — the
resulting `RateLimit<FixedWindowPolicy>` accepts no other policy type. This is guaranteed by
the compiler.

## 2. Define a policy

```java
FixedWindowPolicy policy = new FixedWindowPolicy(
        100,                    // limit: max 100 requests
        Duration.ofMinutes(1)   // window: per minute
);
```

Meaning: *an identifier can make at most 100 requests in any 1-minute window.*

## 3. Check requests

```java
RateLimitResult result = rateLimit.use("user-123", policy);

if (result.isAllowed()) {
    System.out.println("Allowed. Remaining: " + result.getRemaining());
} else {
    System.out.println("Denied. Retry after: " + result.getRetryAfter().orElse(null)
            + ", resets at " + result.getResetAt());
}
```

The same `rateLimit` instance is shared across all identifiers: `use(identifier, policy)`
maintains independent state per identifier. Counters, windows and bursts are handled by the
library — you never read or write state directly.

## Full example

```java
import io.github.lauto5.rateLimit.RateLimit;
import io.github.lauto5.rateLimit.api.Algorithm;
import io.github.lauto5.rateLimit.api.RateLimitResult;
import io.github.lauto5.rateLimit.domain.policies.FixedWindowPolicy;
import io.github.lauto5.rateLimit.inmemory.Persistence;

import java.time.Duration;

public class RateLimitDemo {

    private final RateLimit<FixedWindowPolicy> rateLimit = RateLimit.build(
            Algorithm.fixedWindow(),
            Persistence.inMemory()
    );

    private final FixedWindowPolicy policy =
            new FixedWindowPolicy(100, Duration.ofMinutes(1));

    public boolean tryRequest(String identifier) {
        RateLimitResult result = rateLimit.use(identifier, policy);
        return result.isAllowed();
    }
}
```

## What's next

- Deciding between algorithms → [algorithms/](algorithms/)
- Persistence choices and limits → [persistence/in-memory.md](persistence/in-memory.md) and
  [persistence/redis.md](persistence/redis.md)
- Spring Boot integration → [integrations/spring-boot.md](integrations/spring-boot.md)