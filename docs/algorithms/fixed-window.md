# Fixed Window

## What problem it solves

Limits requests over fixed, non-overlapping periods of time: *at most N requests per minute*
(per hour, per second, ...). The simplest mental model of rate limiting.

## How it works

Time is divided into consecutive windows of `windowSize`. Each window has its own counter:
a request inside the current window consumes one unit; when the window expires, a new window
starts at zero. State per identifier is a single counter + window start, so it is cheap to store
and cheap to compute.

The trade-off: a burst can slip through at window boundaries. With a limit of 100 requests per
minute, **199** requests spaced around the boundary are all allowed (100 at the end of one
window + 100 at the start of the next).

## Usage

```java
import io.github.lauto5.rateLimit.RateLimit;
import io.github.lauto5.rateLimit.api.Algorithm;
import io.github.lauto5.rateLimit.api.RateLimitResult;
import io.github.lauto5.rateLimit.domain.policies.FixedWindowPolicy;
import io.github.lauto5.rateLimit.inmemory.Persistence;

import java.time.Duration;

RateLimit<FixedWindowPolicy> rateLimit = RateLimit.build(
        Algorithm.fixedWindow(),
        Persistence.inMemory()
);

FixedWindowPolicy policy = new FixedWindowPolicy(
        100,                    // limit
        Duration.ofMinutes(1)   // window size
);

RateLimitResult result = rateLimit.use("user-123", policy);
```

`getRemaining()` reports the units left in the current window; `getResetAt()` is the instant
the current window ends and the counter resets.

## When to choose it

Choose Fixed Window when:

- your limits are naturally per-period (per minute / per hour semantics);
- you want minimal state and the cheapest per-request cost;
- the boundary burst (up to 2x) is acceptable — for example, a coarse shaping limit that does
  not protect a scarce resource.

Avoid it when the boundary burst must be eliminated. Prefer
[Sliding Window Counter](sliding-window-counter.md) for near-fixed-window precision with low
memory, or [Sliding Window Log](sliding-window-log.md) for exact precision.