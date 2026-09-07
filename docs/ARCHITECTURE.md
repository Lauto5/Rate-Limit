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
                 |        Public API (api/)        |
                 |   Algorithm, Persistence        |
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
                 +--------+---------------+-------+
                          |               |
              +-----------v----+   +------v------------+
              |    Domain Core  |   |     Ports        |
              |    (domain/)    |   |  (application/   |
              |  Algorithms     |   |    ports/)       |
              |  State / Policy |   |                  |
              |  Models         |   |                  |
              +-----------------+   +------+-----------+
                                           |
                              +------------v------------+
                              |   Infrastructure        |
                              |   (infraestructure/)    |
                              |   InMemoryStore, ...    |
                              +-------------------------+
```

The dependency direction always points **inward**: infrastructure implements ports, the application layer depends on ports, and the domain core depends on nothing.

---

## Public API

The public surface of the library is intentionally minimal:

- **`RateLimit<P>`** -- the facade. Created via `build(algorithm, store)` and consumed via `use(identifier, policy)`.
- **`Algorithm`** -- a static factory for algorithms:
  ```java
  Algorithm.fixedWindow()
  ```
- **`Persistence`** -- a static factory for stores:
  ```java
  Persistence.inMemory()
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
| `RateLimitResultMapper` | Converts internal `AlgorithmResult`/decisions into the public `RateLimitResult` DTO |
| `ports.in.RateLimitResult` | Public result DTO |
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
| `NoOpLogger` | `infraestructure` | Discards every message. **Default** when no logger is supplied, so the library is silent out of the box |
| `ConsoleLogger` | `infraestructure` | Prints timestamped, leveled messages to `stdout` (`stderr` for `ERROR`). Configurable minimum level and logger name |

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
- `RateLimitService` passes the logger to `RateLimitAtomicOperation` and logs request decisions (allowed / denied with `retryAfter`).
- Store implementations (`InMemoryStore`, `RedisStore`, `LettuceKeyValueStore`) accept an optional logger to report state expiration, CAS conflicts, and retries.

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

The solution is a **shared storage backend** (e.g., Redis). All instances read and update the same state, guaranteeing a global limit. <sup>1</sup>

```
  App1  App2  App3
    \    |    /
     \   |   /
      v  v  v
   +---------+
   |  Redis  |
   +---------+
```

<small>1. A Redis store is planned. Because atomicity is already part of the `RateLimitStore` contract, adding a Redis backend does not require any changes to the domain layer -- only a new infrastructure adapter.</small>

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

1. Implement `RateLimitStore` with `executeAtomically()`.
2. Guarantee per-identifier atomicity in the implementation.
3. Enforce state expiration internally (drop expired states when read).
4. Emit store-level diagnostics through an optional `Logger` (state expiration, CAS conflicts, retries).
5. Add a factory method in `api/Persistence.java`.

The `AtomicOperation` and `StoreState` types make the store implementation independent of any specific algorithm.
