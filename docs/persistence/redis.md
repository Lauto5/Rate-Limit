# Redis persistence

`rate-limit-redis` provides `Persistence.inRedis(...)`, a store over a single Redis key per
identifier (`io.github.lauto5.rateLimit.infrastructure.RedisStore`, backed by Lettuce with a
connection pool). Because all application instances read and update the same state, limits are
**global** across processes.

## Configuration

```java
import io.github.lauto5.rateLimit.RateLimit;
import io.github.lauto5.rateLimit.api.Algorithm;
import io.github.lauto5.rateLimit.domain.policies.FixedWindowPolicy;
import io.github.lauto5.rateLimit.redis.Persistence;

RateLimit<FixedWindowPolicy> rateLimit = RateLimit.build(
        Algorithm.fixedWindow(),
        Persistence.inRedis("redis://localhost:6379")
);
```

Factories:

| Method | Notes |
|---|---|
| `inRedis(url)` | Default namespace `rate-limit` |
| `inRedis(url, logger)` | Store-level diagnostics (conflicts, retries, expiry) |
| `inRedis(url, namespace)` | Keys written as `namespace:identifier` |
| `inRedis(url, logger, namespace)` | Both overrides |

### Dependencies

Add a concrete SLF4J provider at runtime (Lettuce requires it), for example
`slf4j-simple`. Runtime is Java 8+.

## How it works

### Atomicity — `WATCH / MULTI / EXEC`

1. A dedicated connection is checked out from the pool (transactions are connection-scoped).
2. `WATCH <key>` marks the key for conflict detection.
3. `GET <key>` reads the current serialized state.
4. The algorithm runs in Java and produces the new state and its TTL.
5. `MULTI` → `SET <key> <value> PX <ttl>` → `EXEC`.
6. If another process modified the key between `WATCH` and `EXEC`, the transaction aborts and
   the operation is retried a bounded number of times with jittered backoff (0–64 ms, max 25
   retries ≈ 1.3 s worst case), then fails loudly instead of guessing.

A `WATCH` conflict is *not* an error (it just means "try again"); a real infrastructure
failure (network, connection, protocol) propagates as `IllegalStateException` immediately.

### Keys and namespaces

Keys are `namespace + ":" + identifier`. The default namespace is `rate-limit`; override it
so applications, environments or versions sharing one Redis instance never collide.

### Expiration (TTL)

Each key is set with a TTL derived from the algorithm result's expiry, so Redis drops the key
when the window lapses — the store does not evaluate `expiresAt` itself. This bounds memory for
inactive identifiers.

### Serialization

Persisted values use a **versioned payload** (`VersionedStateCodec`, magic `"RL"` + format
version + data). Decoding is **fail-closed** (an unknown version raises
`CorruptedStateException`), while the store is **fail-open**: any undecodable value — corrupt,
truncated, or written by a different algorithm — is treated as absent (with a warning) and
rewritten from scratch. Precise limits are never silently corrupted by mismatched data.

## Behaviour under concurrency

- Concurrent requests for the same identifier are safe (per-key atomicity via WATCH); the
  cost is the retry loop under contention.
- Dedicated connections per operation keep transaction state (residual `WATCH`/`MULTI`) away
  from other threads; a used connection is cleaned before returning to the pool.
- Requests for different identifiers are fully parallel.

## When to use it

Multi-instance deployments that must enforce one shared limit, or any need to survive
application restarts. If a single process is enough, see [in-memory](in-memory.md).