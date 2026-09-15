# Leaky Bucket

## What problem it solves

Smooths bursts into a **constant output rate**: requests are processed evenly over time, which
is ideal for backends that cannot absorb spikes. Think of a queue with a fixed drain rate.

## How it works

Requests enter a bucket of `capacity` and drain at a constant `leakRate`. A request is denied
when the bucket is full (it arrived "faster than it can drain"); the remaining capacity bounds
how much of a burst can be queued. Unlike Token Bucket, Leaky Bucket does **not** let bursts
through immediately — it meters them at the drain rate.

The trade-off: output is perfectly smooth, but there is no burst allowance and requests can
experience queueing delay.

## Usage

```java
import io.github.lauto5.rateLimit.RateLimit;
import io.github.lauto5.rateLimit.api.Algorithm;
import io.github.lauto5.rateLimit.api.RateLimitResult;
import io.github.lauto5.rateLimit.domain.policies.LeakyBucketPolicy;
import io.github.lauto5.rateLimit.inmemory.Persistence;

RateLimit<LeakyBucketPolicy> rateLimit = RateLimit.build(
        Algorithm.leakyBucket(),
        Persistence.inMemory()
);

LeakyBucketPolicy policy = new LeakyBucketPolicy(
        10.0,   // capacity: how much of a "spike" fits in the bucket
        1.0     // leakRate: requests drained per second
);

RateLimitResult result = rateLimit.use("user-123", policy);
```

## When to choose it

Choose Leaky Bucket when the consumer behind the limit must see a **uniform rate** — for
example, a legacy integration or a downstream with no buffer, where spikes cause failures even
if the average is fine.

Avoid it when you want to allow short bursts of fast processing ([Token Bucket](token-bucket.md))
or need period semantics ([Fixed Window](fixed-window.md), [Sliding Window Counter](sliding-window-counter.md)).