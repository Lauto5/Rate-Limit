# Sliding Window Log

## What problem it solves

Exact, sliding precision: *no more than N requests in any rolling window*, with no bursts at
boundaries like Fixed Window has.

## How it works

A per-identifier log stores a timestamp for **every** request. A request is allowed if the
number of timestamps inside the last `windowSize` is below the limit; on deny, nothing is
appended. Old timestamps fall out of the window naturally.

Because the check and the window slide continuously with time, the answer is mathematically
exact — there is no boundary effect. The price is **memory**: the log grows with the request
volume within a window, so high-rate identifiers consume O(requests in window) memory.

## Usage

```java
import io.github.lauto5.rateLimit.RateLimit;
import io.github.lauto5.rateLimit.api.Algorithm;
import io.github.lauto5.rateLimit.api.RateLimitResult;
import io.github.lauto5.rateLimit.domain.policies.SlidingWindowLogPolicy;
import io.github.lauto5.rateLimit.inmemory.Persistence;

import java.time.Duration;

RateLimit<SlidingWindowLogPolicy> rateLimit = RateLimit.build(
        Algorithm.slidingWindowLog(),
        Persistence.inMemory()
);

SlidingWindowLogPolicy policy = new SlidingWindowLogPolicy(
        10,                     // limit
        Duration.ofSeconds(5)   // window size
);

RateLimitResult result = rateLimit.use("user-123", policy);
```

Here an identifier may make at most 10 requests in any rolling 5-second span.

## When to choose it

Choose Sliding Window Log when the **exact rolling window** is a hard requirement (for
example, contractual limits) and your identifiers are low-to-moderate traffic, so the
timestamp log stays small.

Avoid it when per-identifier volume is high: memory and store writes scale with request count
within a window. Use [Sliding Window Counter](sliding-window-counter.md) (approximation in
constant memory) or [Token Bucket](token-bucket.md) / [GCRA](gcra.md) (smooth average with a
bounded burst).