# GCRA (Generic Cell Rate Algorithm)

## What problem it solves

Smooth average-rate limiting with a **bounded burst**, using a single value of state per
identifier — the Theoretical Arrival Time (TAT). Originated in ATM network traffic shaping,
and a natural fit for Redis-backed counters.

## How it works

The algorithm computes when a request "should" arrive to stay at the average `rate`:

- `emissionInterval = 1 / rate` — the spacing of a steady stream.
- Each request advances the TAT by the emission interval, unless the bucket is idle
  (`TAT < now` → the effective TAT starts at `now`).
- A request is allowed if `TAT <= now + burst` (the burst is the *tolerance time* the stream
  may run ahead); otherwise it is denied and the TAT is not advanced.

Allowed requests arriving *early* accumulate "debt" up to the burst tolerance; once the debt
exceeds it, requests are denied until the stream catches up. The state is a single timestamp,
so it is the cheapest exact-ish algorithm and fits a single Redis key per identifier.

## Usage

```java
import io.github.lauto5.rateLimit.RateLimit;
import io.github.lauto5.rateLimit.api.Algorithm;
import io.github.lauto5.rateLimit.api.RateLimitResult;
import io.github.lauto5.rateLimit.domain.policies.GcraPolicy;
import io.github.lauto5.rateLimit.inmemory.Persistence;

import java.time.Duration;

RateLimit<GcraPolicy> rateLimit = RateLimit.build(
        Algorithm.gcra(),
        Persistence.inMemory()
);

GcraPolicy policy = new GcraPolicy(
        1.0,                     // rate: average requests per second
        Duration.ofSeconds(5)    // burst: max lead time tolerated (spike)
);

RateLimitResult result = rateLimit.use("user-123", policy);
```

With `rate = 1` and `burst = 5s`, an idle identifier can fire roughly 5 requests at once, then
is held to 1 request/second.

## When to choose it

Choose GCRA when you want the Token Bucket behaviour (average rate + bounded burst) with the
**smallest possible state**, especially with a Redis store: one timestamp per identifier,
cheap to serialize and to update inside a transaction. It is the same contract as Token Bucket
expressed as a single number (the two are mathematically equivalent for these parameters).

Avoid it when `capacity + refillRate` semantics are easier for your team to communicate
(Token Bucket reads more intuitively) or when you need exact rolling-window counts
([Sliding Window Log](sliding-window-log.md)).