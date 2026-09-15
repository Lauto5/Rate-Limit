# In-memory persistence

`rate-limit-inmemory` provides `Persistence.inMemory()`, a store backed by a
`ConcurrentHashMap` (`io.github.lauto5.rateLimit.infrastructure.InMemoryStore`). All state lives
inside the JVM process.

## Configuration

```java
import io.github.lauto5.rateLimit.RateLimit;
import io.github.lauto5.rateLimit.api.Algorithm;
import io.github.lauto5.rateLimit.domain.policies.FixedWindowPolicy;
import io.github.lauto5.rateLimit.inmemory.Persistence;

RateLimit<FixedWindowPolicy> rateLimit = RateLimit.build(
        Algorithm.fixedWindow(),
        Persistence.inMemory()
);
```

Factories:

| Method | Notes |
|---|---|
| `inMemory()` | No logging |
| `inMemory(logger)` | Emits store-level diagnostics (expired-state drops, etc.) through the given `Logger` |

## Characteristics

- **Correct for a single instance.** Limits are per-JVM. In multi-instance deployments each
  instance would count independently and the effective limit would be multiplied — use
  [Redis](redis.md) for a global limit.
- **Thread-safe.** Concurrent requests for the same identifier are serialized internally;
  requests for different identifiers run in parallel.
- **Expiry on read.** Expired entries are dropped lazily when an identifier is read again; the
  map does not evict on a timer.
- **Runtime:** Java 8+. **Dependencies:** none beyond the JDK — the module has no third-party
  runtime dependencies.

## When to use it

- Single-instance applications, development, local tests, quick prototypes.
- As a reference or contract baseline (a Redis adapter must behave identically; that is
  enforced by a shared contract test in the project).

## Limitations

- State is not shared across processes or restarts (a JVM restart resets all limits).
- Without periodic eviction, entries for inactive identifiers remain in the map until they are
  read again; long-lived processes with unbounded identifiers grow memory until reads clean
  expired states.