# Sliding Window Counter

## What problem it solves

Removes most of the Fixed Window boundary burst while keeping **constant, small memory**.
Positioned between the simplicity of Fixed Window and the exactness of Sliding Window Log.

## How it works

The window of `windowSize` is divided into `subWindows` slots. Each slot keeps a count.
Because a request's window slides, the current count is a **weighted** sum of the completed
slot and the fraction of the current slot that overlaps the window:

```text
value ≈ (fraction of the previous slot still in the window × its count) + current slot count
```

The approximation assumes requests within a slot are spread uniformly. Memory is proportional
to `subWindows` per identifier (constant, regardless of traffic), and precision increases with
the number of sub-windows — at the cost of a slightly larger state.

## Usage

```java
import io.github.lauto5.rateLimit.RateLimit;
import io.github.lauto5.rateLimit.api.Algorithm;
import io.github.lauto5.rateLimit.api.RateLimitResult;
import io.github.lauto5.rateLimit.domain.policies.SlidingWindowCounterPolicy;
import io.github.lauto5.rateLimit.inmemory.Persistence;

import java.time.Duration;

RateLimit<SlidingWindowCounterPolicy> rateLimit = RateLimit.build(
        Algorithm.slidingWindowCounter(),
        Persistence.inMemory()
);

SlidingWindowCounterPolicy policy = new SlidingWindowCounterPolicy(
        100,                        // limit
        Duration.ofMinutes(1),      // window size
        10                          // subWindows within the window
);

RateLimitResult result = rateLimit.use("user-123", policy);
```

Here the minute is split into 10 six-second slots; the effective limit in any rolling span is a
weighted approximation of 100.

## When to choose it

Choose Sliding Window Counter when you want the rolling-window behaviour — without the exactness
cost of a log — and can accept a small approximation error. It's a good default for most
production API limits: bounded memory, cheaper than the log, far smoother than Fixed Window.

Avoid it when the limit must be exact ([Sliding Window Log](sliding-window-log.md)) or when a
smooth average with bursts fits the product better ([Token Bucket](token-bucket.md), [GCRA](gcra.md)).