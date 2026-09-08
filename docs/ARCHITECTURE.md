# Architecture

This document describes the internal architecture of Rate Limit. It is intended for contributors and anyone interested in how the library is designed.

---

## Table of Contents

- [Overview](#overview)
- [Design Principles](#design-principles)
- [Layers](#layers)
  - [Public API](#public-api)
  - [Application Layer](#application-layer)
  - [Domain Layer](#domain-layer)
  - [Infrastructure Layer](#infrastructure-layer)
- [Core Abstractions](#core-abstractions)
- [Request Lifecycle](#request-lifecycle)
- [Logging](#logging)
- [State Management](#state-management)
  - [State Lifecycle](#state-lifecycle)
  - [Expiration](#expiration)
- [Concurrency and Atomicity](#concurrency-and-atomicity)
- [Distributed Rate Limiting](#distributed-rate-limiting)

---

## Overview

Rate Limit is built using **hexagonal architecture** (also known as *ports and adapters*). This approach separates the **core domain logic** from **external concerns** like persistence and time, making the library:

- **Framework-agnostic** -- no dependency on Spring, Micronaut, Vert.x, or any application framework.
- **Storage-agnostic** -- the same algorithm works with an in-memory map, Redis, or any future backend.
- **Testable** -- domain logic can be fully exercised without real infrastructure.

The core depends only on abstractions (interfaces) and never on concrete technologies.

---

## Design Principles

1. **Dependency inversion** -- high-level modules (algorithms) depend on abstractions, never on low-level modules (stores).
2. **Type safety** -- every algorithm is paired with its own state and policy types through Java generics. The compiler enforces that you cannot mix incompatible configurations.
3. **Library-managed state** -- users interact only with a policy and an identifier; they never create, hold, or persist state directly.
4. **Atomic operations** -- the store guarantees that the read-modify-write cycle for a given identifier happens atomically, preventing lost updates under concurrency.

---

## Layers

```
                 +---------------------------------+
                 |  Public API (api/)              |
                 |  Algorithm, RateLimitResult     |
                 +----------------+----------------+
                                  |
                                  v
                 +---------------------------------+
                 |    Application Layer           |
                 |    (application/)              |
                 |  - Service orchestration       |
                 |  - Adapters                    |
                 |  - Result mapping              |
                 |  - Ports (contracts)           |
                 |  - VersionedStateCodec         |
                 +--------+---------------+-------+
                          |               |
              +-----------v----+   +------v------------+
               |    Domain Core  |   |     Ports        |
               |    (domain/)    |   |  (application/   |
               |  Algorithms     |   |    ports/)       |
               |  State / Policy |   |  AtomicOperation |
               |  Codecs/Models  |   |  Logger          |
               +-----------------+   |  RateLimitStore  |
                                     |  StoreState      |
                                     +-------------------+
                                            |
                    +-----------------------+-----------------------+
                    v                       v                       v
          +------------------+  +------------------------+  +-------------------+
          | rate-limit-      |  | rate-limit-redis       |  | examples/*        |
          | inmemory         |  | Infrastructure:        |  | standalone apps   |
          | infrastructure/  |  |  LettuceTransaction    |  +-------------------+
          |  InMemoryStore   |  |  RedisStore            |
          +------------------+  |  RedisTransactionPort  |
                                +------------------------+
```

The Maven reactor mirrors this separation: `rate-limit-core` holds the API, application and domain layers; `rate-limit-inmemory` and `rate-limit-redis` are separate modules holding only infrastructure adapters. The dependency direction always points **inward**: infrastructure modules depend on the core and implement its ports, the application layer depends on ports, and the domain core depends on nothing.

---

## Public API

The public surface of the library is intentionally minimal:

- **`RateLimit<P>`** -- the facade. Created via `build(algorithm, store)` and consumed via `use(identifier, policy)`.
- **`Algorithm`** -- a static factory for algorithms:
  ```java
  Algorithm.fixedWindow()
  ```
- **`Persistence`** -- a module-scoped static factory for stores. Each persistence module defines its own factory class so unused infrastructure never leaks onto the classpath:
  ```java
  io.github.lauto5.rateLimit.inmemory.Persistence.inMemory()
  io.github.lauto5.rateLimit.redis.Persistence.inRedis("redis://localhost:6379")
  ```
- **`RateLimitResult`** -- the return value with `allowed`, `remaining`, `retryAfter`, and `resetAt`.

### Why a single entry point?

Keeping the public API small reduces the learning curve and protects the design. Users should not need to know about states, atomic operations, or store internals.

---

## Application Layer

Located in `application/`. This layer orchestrates the flow between the public API and the domain core.

| Component | Responsibility |
|---|---|---|
| `RateLimitExecutor` | Port (inbound) defining the `execute()` command |
| `RateLimitService` | Orchestrates a full `use()` call: builds context, runs the atomic operation, maps the result. Receives the `Logger` and emits per-request logs |
| `RateLimitAtomicOperation` | Adapter that wraps an algorithm call into the atomic operation the store can execute |
| `RateLimitResultMapper` | Converts internal `AlgorithmResult`/decisions into the public `RateLimitResult` DTO (in `api/`) |
| `VersionedStateCodec` | Serializes/deserializes state with a magic marker and a format-version prefix; throws `CorruptedStateException` on invalid data |
| `CorruptedStateException` | Signals state bytes that cannot be decoded (bad magic, unknown version, truncated payload) |
| `ports.out.*` | Outbound contracts the store must implement (including `Logger`) |

---

## Domain Layer

Located in `domain/`. This is the heart of the library and is **pure Java** with no framework or infrastructure dependencies.

| Package | Contents |
|---|---|
| `algorithm` | `RateLimitAlgorithm` contract + concrete algorithms |
| `algorithmState` | Immutable state value objects (one per algorithm) |
| `policies` | Immutable policy value objects (one per algorithm) |
| `context` | `AlgorithmContext` (captures the current instant) |
| `model` | `AlgorithmResult`, decisions (`Allowed`, `Denied`) |

### Key domain abstractions

**`RateLimitAlgorithm<S, P>`** -- the contract every algorithm implements:

```java
public interface RateLimitAlgorithm<S extends AlgorithmState, P extends RateLimitPolicy> {
    AlgorithmResult<S> execute(S state, P policy, AlgorithmContext context);
    S createInitialState(P policy, AlgorithmContext context);
}
```

- `execute()` receives the current state and returns a result with a new state **and** a decision.
- `createInitialState()` builds the starting state when no state exists yet.

**`RateLimitPolicy`** -- a marker interface. Each algorithm defines its own policy type (e.g., `FixedWindowPolicy(limit, windowSize)`).

**`AlgorithmState`** -- a marker interface. Each algorithm defines its own immutable state type (e.g., `FixedWindowState(count, windowStart)`).

**`AlgorithmResult<S>`** -- carries the new state, the decision, the reset instant, and the expiration duration for the state.

---

## Core Abstractions

### Ports (contracts the library offers)

| Interface | Location | Purpose |
|---|---|---|
| `RateLimitStore` | `ports.out` | Persistence contract; must execute operations atomically |
| `AtomicOperation<S>` | `ports.out` | A unit of work applied to a store state, producing a new state |
| `StoreState<S>` | `ports.out` | Wraps a state with its expiration time |
| `Logger` | `ports.out` | Outbound contract for diagnostic output; injected at build time and propagated through the pipeline |
| `RateLimitExecutor<P>` | `application` | Inbound port for the top-level use case |

### Important generics

The three core types are parameterized so the compiler guarantees consistency:

```java
RateLimitAlgorithm<S, P>   // S = state type, P = policy type
RateLimit<P>               // only the policy type leaks to the user
```

This means a `FixedWindowAlgorithm` can only be used with `FixedWindowState` and `FixedWindowPolicy`. Mixing them is a compile-time error.

---

## Request Lifecycle

The typical flow for a single `use(identifier, policy)` call:

```
1. User calls rateLimit.use("user-123", policy)
                     |
2. RateLimitService builds an AlgorithmContext (current time)
                     |
3. RateLimitService creates a RateLimitAtomicOperation
   (algorithm + policy + context)
                     |
4. RateLimitService calls store.executeAtomically(identifier, operation)
                     |
5. Store retrieves the current state for that identifier
   - If missing or expired -> null
                     |
6. Operation.apply(state):
   - If state is null  -> algorithm.createInitialState()
   - Else              -> use existing state
   - Run algorithm.execute(state, policy, context)
   - Compute new expiration time
   - Return AtomicOperationResult(newState, newExpiresAt)
                     |
7. Store atomically persists the new { state, expiresAt }
   and returns the result (could not be saved, e.g. denied)
                     |
8. RateLimitResultMapper converts the result into the public
   RateLimitResult DTO (allowed / remaining / retryAfter / resetAt)
```

---

## Logging

The library exposes a pluggable `Logger` port so diagnostic output can be routed to any backend without coupling the core to a specific framework.

### The port

```java
public interface Logger {
    enum Level { DEBUG, INFO, WARN, ERROR }
    void log(Level level, String message);
    // default helpers: debug(), info(), warn(), error()
}
```

### Built-in implementations

| Implementation | Location | Behavior |
|---|---|---|
| `NoOpLogger` | `application/logging/` (core) | Discards every message. **Default** when no logger is supplied, so the library is silent out of the box |
| `ConsoleLogger` | `application/logging/` (core) | Prints timestamped, leveled messages to `stdout` (`stderr` for `ERROR`). Configurable minimum level and logger name |

### Propagation

The logger is threaded **inward** following the same dependency rule as every other port:

```
RateLimit.build(algorithm, store, clock, logger)
      |
      v
RateLimitService (logger)
      |
      +-> RateLimitAtomicOperation (logger)
      |
      v
RateLimitStore / stores (logger, for store-level diagnostics)
```

- `RateLimit.build(...)` accepts a `Logger` via overloads; the existing overloads default to `NoOpLogger`.
- `RateLimitService` passes the logger to `RateLimitAtomicOperation` and logs request decisions (allowed / denied with `retryAfter`). **Decisions are emitted at `DEBUG`** -- with hundreds of log lines per second per identifier at scale, per-request decisions don't default to `INFO`/`WARN`; stores log expiry (`WARN`) and the service keeps loud levels for actual problems only.
- Store implementations (`InMemoryStore`, `RedisStore`) accept an optional logger to report state expiration, `WATCH` conflicts, and retries.

This keeps the domain layer **free of logging concerns**: algorithms, states, and policies never reference the `Logger`.

---

## State Management

### State Lifecycle

Unlike libraries such as bucket4j, Rate Limit **fully manages state on the user's behalf**. Users only provide an identifier and a policy.

| Stage | Who handles it |
|---|---|
| **Creation** | The algorithm's `createInitialState()` when a new identifier is seen |
| **Read** | The store, when retrieving state for an identifier |
| **Update** | The algorithm's `execute()`, producing a new immutable state |
| **Persistence** | The store, atomically within `executeAtomically()` |
| **Expiration** | The store, based on the expiry computed by the algorithm |

### Expiration

Every state has an expiration time to prevent unbounded memory growth for inactive identifiers:

- Each identifier's state is stored with an `expiresAt` timestamp.
- The **algorithm** decides the expiration (e.g., the end of the current window).
- The **store** is responsible for enforcing it internally (e.g., dropping expired entries when read).

When a request arrives for an expired state, the store treats it as absent, and the algorithm creates a brand-new initial state.

**Invariant:** `expiresAt` is *persistence metadata* only. Algorithms never read it back --
the `AlgorithmState` carried by the state object holds its own time-related fields (window
start, TAT, last refill). Expiry handling is therefore a store concern, enforced with each
store's own mechanism:

- `InMemoryStore` checks `StoreState.isExpired()` against the operation's clock on read;
- `RedisStore` derives the key TTL from the operation result's `expiresAt` (`SET ... PX ttl`)
  and lets Redis drop the key when it lapses -- it does **not** evaluate `expiresAt` itself.

---

## Concurrency and Atomicity

Rate limiting is inherently a read-modify-write problem. Naive implementations can lose updates:

```
Request A reads count = 99
Request B reads count = 99
A persists 100
B persists 100   <- lost update!
```

To prevent this, the `RateLimitStore.executeAtomically()` contract guarantees that, for a **given identifier**, the operation runs atomically:

```java
public interface RateLimitStore {
    <S extends AlgorithmState> AtomicOperationResult<S>
    executeAtomically(String identifier, AtomicOperation<S> operation);
}
```

**Concurrent requests for the same identifier are serialized** at the store level. Requests for **different identifiers** remain fully parallel, preserving throughput.

> Note: atomicity for a single identifier does not by itself solve cross-instance consistency in a distributed deployment. See below.

---

## Distributed Rate Limiting

With the included `InMemoryStore`, each application instance keeps its own local state. This is correct for **single-instance** deployments but produces *inaccurate* limits in a **multi-instance** deployment, because each instance would count independently.

| Scenario | Behavior |
|---|---|
| Single instance | Accurate (all requests hit one store) |
| Multi-instance with in-memory stores | Each instance has its own counter -> limit is effectively multiplied |

The solution is a **shared storage backend** (e.g., Redis). All instances read and update the same state, guaranteeing a global limit.

```
  App1  App2  App3
    \    |    /
     \   |   /
      v  v  v
   +---------+
   |  Redis  |
   +---------+
```

### How the Redis store works

The `RedisStore` in `rate-limit-redis` implements the `RateLimitStore` contract over a single Redis key per identifier using the **`WATCH / MULTI / EXEC`** protocol:

1. Check out a dedicated connection from the Lettuce connection pool.
2. `WATCH <key>` -- the Redis server tracks changes to that key for this connection.
3. `GET <key>` -- read the current serialized state; missing or undecodable state is treated as absent.
4. Run the algorithm in Java; build the new serialized state and its TTL.
5. `MULTI`, then `SET <key> value PX ttl`, then `EXEC`.
6. If another process modified the key between `WATCH` and `EXEC`, the transaction aborts (`wasDiscarded()`); the whole operation is retried with a bounded retry count and jittered exponential backoff to avoid starvation under contention.

**Keys are namespaced** (`key = namespace + ":" + identifier`, default namespace
`rate-limit`, configurable via `RedisStore`/`redis.Persistence` overloads). The namespace
isolates applications, environments, or versions sharing the same Redis, so they never
collide on keys.

**Failure semantics:** a WATCH conflict is not an error -- it returns `null` and the store
retries. An infrastructure failure (network, connection, protocol) propagates immediately as
`IllegalStateException` without being masked as a conflict. In the worst case, persistent
contention on a single key exhausts the bounded retries (currently `MAX_RETRIES = 25` with
0..64 ms jitter), adding at most ~1.3 s of latency and then failing loud instead of guessing.

Each unit of work runs on a **dedicated connection** because Redis transactions and `WATCH` are connection-scoped; interleaving `MULTI`/`EXEC` calls across threads on a shared connection is not allowed by the Redis protocol. Before a connection returns to the pool it is guaranteed clean: a failed body/network/protocol path runs best-effort `DISCARD` (if inside `MULTI`) or `UNWATCH` (if only `WATCH` was issued) as `LettuceTransactionPort.cleanupTransactionState`, so a connection with residual `WATCH`/`MULTI` state is never handed to another thread.

Persisted values are encoded with `VersionedStateCodec` (`"RL"` magic + version byte + payload). Because the codec is part of the core, states written by any version of the library remain forward-readable; corrupt or truncated data is treated as absent (with a warning) and rewritten from scratch. The versioning policy is **fail-closed on decode** (an unknown version raises `CorruptedStateException`, never ambiguous interpretation) but **fail-open at the store** (that state is treated as absent and rebuilt), which together keep a mismatched version from silently corrupting counters. The boundary is total: the decorator also normalizes any failure of the concrete codec to interpret a *payload with a valid header* (for example a `NumberFormatException` when decoding a state from a different algorithm under the same key) into `CorruptedStateException`, so no decode path aborts a request -- every undecodable state is rebuilt from scratch.

Because atomicity for a single identifier is already part of the `RateLimitStore` contract, adding a Redis backend required **no changes** to the domain layer -- only a new infrastructure adapter in its own module.

### Test of contract between stores

`rate-limit-redis` carries a **contract test** (`StoreContractTest`) that depends on
`rate-limit-inmemory` (test scope) and runs the *same* fixed-window sequence against both
stores, asserting identical allowed/denied decisions -- proving the Redis adapter is a
drop-in replacement for the in-memory one. It also stresses one shared key with **1000
concurrent operations** (limit 100) and asserts `allowed <= limit` under real contention.

---

## Phase 3 -- Final review and merge decision

Closing audit of the feature: every reviewed area was classified
(🔴 fix / 🟠 adjust / 🟡 document / 🟢 validated):

| Audited area | Classification | Justification |
|---|---|---|
| `RedisStore` (retries, backoff, TTL, namespace, validation) | 🟢 | Bounded `MAX_RETRIES=25`, backoff 0..64 ms with restored interruption, TTL with a 1 ms floor, namespaced keys, validated identifier; covered by `RedisStoreUnitTest`. |
| `LettuceTransactionPort` (connection cleanup, pool, conflict vs infra) | 🟢 | Best-effort `DISCARD`/`UNWATCH` before returning to the pool; `WATCH` conflicts return `null`, infrastructure errors propagate; connection reusable after a body failure. |
| Real concurrency | 🟢 | `LettuceTransactionPortIntegrationTest$ConcurrencyCases` (barrier), 20 threads in `RedisStoreUnitTest`, 1000 operations over one key in `StoreContractTest`. |
| WATCH/MULTI/EXEC vs infrastructure errors | 🟢 | `infraErrorShouldPropagateWithoutRetrying`, `maxRetriesShouldBeExhaustedOnPersistentConflict`. |
| TTL / expiration | 🟢 | Invariant `StoreState.expiresAt` = metadata; Redis uses `PEXPIRE`, InMemory uses `isExpired` on read; the contract test confirms identical decisions. |
| State corruption (fail-open/fail-closed) | 🟠 → 🟢 | Policy documented and fully covered: any undecodable payload (even with a valid header) is normalized to `CorruptedStateException` and the store rewrites it from scratch. |
| Maven dependencies | 🟠 → 🟢 | Direction `inmemory/redis -> core` respected; the dead `slf4j-simple` entry was removed from the parent's `dependencyManagement`. The consumer chooses their SLF4J provider. |
| Public API | 🟡 | `api/`, `Persistence` and policies form the supported surface. `application`/`infrastructure` classes are public for package cohesion; sealing them is future work (`feature/api-v2`). |
| Integration tests | 🟢 | Testcontainers (`redis:7-alpine`) in integration and contract tests between `InMemoryStore` and `RedisStore`. |
| Java 8 (runtime) vs build JDK | 🟢 | `--release 8` + enforcer `[9,)`; policy documented in README. |
| Documentation | 🟢 | README, ARCHITECTURE.md and CONTRIBUTING.md up to date. |

Documented decisions that remain **explicitly outside this branch** (future features):

- `feature/redis-hardening` -- fixed connection pool (`maxTotal=8`, indefinite blocking when exhausted) is not configurable; make it configurable and measure queue latency.
- `feature/inmemory-eviction` -- `InMemoryStore` does not evict expired entries; map growth is bounded for arbitrary identifiers.
- `feature/api-v2` -- seal the internal `application`/`infrastructure` classes and reduce the double surface of `RateLimitResult` (constructor + factories).
- `feature/redis-lua-atomic-operations` -- Lua scripts replacing WATCH/MULTI/EXEC.
- `feature/observability` -- Micrometer / metrics for conflicts, retries and latency.
- `feature/spring-boot-integration` -- autoconfiguration for Spring Boot.

**Decision:** the feature is ready to merge to `main`. Every point of the phase 3
criterion is met: no pending `🔴`, `🟠` resolved (with tests), `🟡` documented, `🟢`
validated, full suite green (231 tests) and the Java 8 / build JDK policy documented.

---

## Adding a New Algorithm

To add a new rate limiting algorithm:

1. **Create the state** -- `domain/algorithmState/YourState.java` implementing `AlgorithmState`.
2. **Create the policy** -- `domain/policies/YourPolicy.java` implementing `RateLimitPolicy`.
3. **Create the marker interface** -- `domain/algorithm/YourAlgorithm.java` extending `RateLimitAlgorithm<YourState, YourPolicy>`.
4. **Implement the algorithm** -- `YourAlgorithmImpl` with `execute()` and `createInitialState()`.
5. **Add a factory method** -- in `api/Algorithm.java`.
6. **Add tests** -- cover state creation, allowed/denied decisions, expiry, and edge cases.

The rest of the pipeline (atomic operation, mapping, store) is generic and needs no changes.

---

## Adding a New Store

To add a new storage backend:

1. Create a new Maven module (e.g. `rate-limit-<backend>`) depending on `rate-limit-core`.
2. Implement `RateLimitStore` with `executeAtomically()`.
3. Guarantee per-identifier atomicity in the implementation (e.g. in-memory locking, Redis `WATCH/MULTI/EXEC`).
4. Enforce state expiration internally (drop expired states when read).
5. Emit store-level diagnostics through an optional `Logger` (state expiration, watch conflicts, retries).
6. Add a `<backend>.Persistence` factory class in that module (mirroring `inmemory.Persistence` / `redis.Persistence`) so the public surface stays at a single entry point while dependencies stay minimal.

The `AtomicOperation`, `StoreState`, and `AtomicOperationResult` types (the codec used by the operation is a `domain.algorithm.StateCodec`) make the store implementation independent of any specific algorithm.
