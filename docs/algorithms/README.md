# Algorithms

Every algorithm implements the same contract: given a policy and the current (typed) state, it
decides whether a request is allowed and produces the next state. Each policy class brings its
own parameters, so choose an algorithm first, then build your policy.

| Algorithm | Doc | Good when | Trade-off |
|---|---|---|---|
| Fixed Window | [fixed-window.md](fixed-window.md) | Simple per-period limits | Bursts at window boundaries |
| Sliding Window Log | [sliding-window-log.md](sliding-window-log.md) | Exact precision required | High memory |
| Sliding Window Counter | [sliding-window-counter.md](sliding-window-counter.md) | Good precision, low memory | Approximation |
| Token Bucket | [token-bucket.md](token-bucket.md) | Controlled bursts, average rate | Fine-grained state |
| Leaky Bucket | [leaky-bucket.md](leaky-bucket.md) | Smooth, constant output | No bursts |
| GCRA | [gcra.md](gcra.md) | Extreme precision, minimal state | Complex concept |

All examples build a `RateLimit<P>` the same way:

```java
RateLimit<SomePolicy> rateLimit = RateLimit.build(Algorithm.someAlgorithm(), store);
```

where `store` is any `RateLimitStore` (see [persistence](../persistence/)). The algorithm is
selected through `io.github.lauto5.rateLimit.api.Algorithm`, and the policy type follows from
it, enforced at compile time.