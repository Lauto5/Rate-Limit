# Token Bucket

## What problem it solves

Allows an average request rate while permitting **controlled bursts**: *on average N requests
per second, but short spikes are tolerated up to a cap*. Widely used shape for API gateways
(e.g. AWS, Stripe).

## How it works

A bucket holds up to `capacity` tokens. Each request consumes one token. Tokens are refilled
continuously at `refillRate` tokens per second, up to the bucket capacity (unused capacity is
never accumulated beyond the cap).

- Full bucket → a burst of up to `capacity` requests is allowed immediately.
- Empty bucket → requests are denied until tokens are refilled.
- A steady flow at a rate ≤ `refillRate` is always allowed once any burst has been drained.

Only one value (current token count) plus a refill timestamp is stored per identifier, so
memory stays low.

## Usage

```java
import io.github.lauto5.rateLimit.RateLimit;
import io.github.lauto5.rateLimit.api.Algorithm;
import io.github.lauto5.rateLimit.api.RateLimitResult;
import io.github.lauto5.rateLimit.domain.policies.TokenBucketPolicy;
import io.github.lauto5.rateLimit.inmemory.Persistence;

RateLimit<TokenBucketPolicy> rateLimit = RateLimit.build(
        Algorithm.tokenBucket(),
        Persistence.inMemory()
);

TokenBucketPolicy policy = new TokenBucketPolicy(
        10.0,   // capacity: max burst size (tokens)
        1.0     // refillRate: tokens added per second
);

RateLimitResult result = rateLimit.use("user-123", policy);
```

Here a cold identifier can fire 10 requests instantly, then is limited to 1 request/second.
`getRetryAfter()` (present when denied) is estimated from the next refill.

## When to choose it

Choose Token Bucket when you need to **allow bursts** while keeping a sustained average —
for example absorbing a marketing spike without overwhelming a backend. It gives the most
intuitive control of *rate vs. burst*.

Avoid it if your requirement is literally "no more than N per period" with strict period
semantics and no bursts — [Fixed Window](fixed-window.md) is simpler to reason about there.
For refund-style sharing of a fixed per-period allowance, [GCRA](gcra.md) is equivalent with
even fewer moving parts.