# Rate Limit

[![Maven Central](https://img.shields.io/maven-central/v/io.github.lauto5/rate-limit.svg)](https://central.sonatype.com/artifact/io.github.lauto5/rate-limit/1.0.0)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Java](https://img.shields.io/badge/Java-17+-blue.svg)](https://www.oracle.com/java/)

A lightweight, extensible rate limiting library for Java applications built on hexagonal architecture principles.

Rate Limit provides a clean, type-safe API for enforcing request quotas using multiple algorithms, with pluggable storage backends. The library is split into Maven modules so each application only depends on the persistence it actually uses.

---

## Table of Contents

- [Features](#features)
- [Supported Algorithms](#supported-algorithms)
- [Requirements](#requirements)
- [Modules](#modules)
- [Installation](#installation)
- [Quick Start](#quick-start)
- [Logging](#logging)
- [Algorithms](#algorithms)
  - [Fixed Window](#fixed-window)
  - [Sliding Window Log](#sliding-window-log)
  - [Sliding Window Counter](#sliding-window-counter)
  - [Token Bucket](#token-bucket)
  - [Leaky Bucket](#leaky-bucket)
  - [GCRA](#gcra)
- [Storage Backends](#storage-backends)
- [Architecture](#architecture)
- [API Reference](#api-reference)
- [Contributing](#contributing)
- [License](#license)

---

## Features

- **Multiple algorithms** -- Choose from 6 industry-standard rate limiting algorithms, each suited for different use cases.
- **Pluggable, module-based storage** -- Swap storage backends without changing application logic. Ship with an in-memory store and a Redis-backed store, each in its own Maven module.
- **Hexagonal architecture** -- Clean separation between domain logic and infrastructure. No framework coupling.
- **Type-safe configuration** -- Each algorithm has its own strongly-typed policy class. No magic strings or generic configuration maps.
- **Thread-safe** -- Designed for concurrent environments out of the box.
- **Pluggable logging** -- Inject your own `Logger` at build time to emit diagnostic output across the whole pipeline. Silent by default, with a `ConsoleLogger` included.
- **Minimal core dependencies** -- `rate-limit-core` only requires the Java standard library at runtime. Infrastructure concerns (e.g. Lettuce for Redis) live in their own modules.

---

## Supported Algorithms

| Algorithm | Precision | Memory | Burst Support | Status |
|---|---|---|---|---|
| Fixed Window | Medium | Low | No | Implemented |
| Sliding Window Log | High | High | No | Implemented |
| Sliding Window Counter | High | Low | No | Implemented |
| Token Bucket | High | Medium | Yes | Implemented |
| Leaky Bucket | Medium | Medium | No | Implemented |
| GCRA | High | Low | Yes | Implemented |

---

## Requirements

- **Java** 8 or later to run (the build compiles with `--release 8`, so a JDK 9+ is required to build)
- **Maven** 3.9+

---

## Modules

The project is a Maven multi-module reactor:

| Module | Contents |
|---|---|
| `rate-limit-core` | Public API, domain algorithms, application layer and ports. No infrastructure dependencies. |
| `rate-limit-inmemory` | `InMemoryStore` adapter for single-instance, development and testing scenarios. |
| `rate-limit-redis` | `RedisStore` adapter backed by Lettuce for distributed rate limiting across processes. |
| `examples/*` | Standalone example projects consuming the packaged jars. |

---

## Installation

Add `rate-limit-core` plus the persistence module you need.

### In-memory (single instance)

```xml
<dependency>
    <groupId>io.github.lauto5</groupId>
    <artifactId>rate-limit-core</artifactId>
    <version>1.0.0</version>
</dependency>
<dependency>
    <groupId>io.github.lauto5</groupId>
    <artifactId>rate-limit-inmemory</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Redis (distributed)

```xml
<dependency>
    <groupId>io.github.lauto5</groupId>
    <artifactId>rate-limit-core</artifactId>
    <version>1.0.0</version>
</dependency>
<dependency>
    <groupId>io.github.lauto5</groupId>
    <artifactId>rate-limit-redis</artifactId>
    <version>1.0.0</version>
</dependency>
```

> The `rate-limit-redis` module uses Lettuce and therefore needs a concrete SLF4J provider at
> runtime (e.g. `slf4j-simple`). The in-memory module has no third-party runtime dependencies.

### Gradle

```groovy
implementation 'io.github.lauto5:rate-limit-core:1.0.0'
implementation 'io.github.lauto5:rate-limit-inmemory:1.0.0'
```

---

## Quick Start

```java
import io.github.lauto5.rateLimit.RateLimit;
import io.github.lauto5.rateLimit.api.Algorithm;
import io.github.lauto5.rateLimit.api.RateLimitResult;
import io.github.lauto5.rateLimit.domain.policies.FixedWindowPolicy;
import io.github.lauto5.rateLimit.inmemory.Persistence;

import java.time.Duration;

// 1. Build the rate limiter
RateLimit<FixedWindowPolicy> rateLimit = RateLimit.build(
        Algorithm.fixedWindow(),
        Persistence.inMemory()
);

// 2. Define a policy
FixedWindowPolicy policy = new FixedWindowPolicy(
        100,                    // max 100 requests
        Duration.ofMinutes(1)   // per minute
);

// 3. Check every request
RateLimitResult result = rateLimit.use("user-123", policy);

if (result.isAllowed()) {
    // Process the request
    System.out.println("Remaining: " + result.getRemaining());
} else {
    // Reject the request
    System.out.println("Retry after: " + result.getRetryAfter().get());
    System.out.println("Resets at: " + result.getResetAt());
}
```

---

## Logging

Rate Limit exposes a pluggable `Logger` port (`io.github.lauto5.rateLimit.application.ports.out.Logger`) with `DEBUG`, `INFO`, `WARN`, and `ERROR` levels. By default the library is **silent** (a `NoOpLogger` is used). To opt in, inject a logger via the `build` overloads:

```java
import io.github.lauto5.rateLimit.RateLimit;
import io.github.lauto5.rateLimit.api.Algorithm;
import io.github.lauto5.rateLimit.inmemory.Persistence;
import io.github.lauto5.rateLimit.application.logging.ConsoleLogger;
import io.github.lauto5.rateLimit.domain.policies.FixedWindowPolicy;

// The built-in console logger emits INFO+ by default
ConsoleLogger logger = new ConsoleLogger(RateLimit.class);

RateLimit<FixedWindowPolicy> rateLimit = RateLimit.build(
        Algorithm.fixedWindow(),
        Persistence.inMemory(logger),
        logger
);
```

You can also lower the threshold to see `DEBUG` output:

```java
ConsoleLogger logger =
        new ConsoleLogger(RateLimit.class, ConsoleLogger.Level.DEBUG);
```

### What gets logged

The logger is propagated through the full pipeline, so you get visibility into:

- **Requests** -- each `use()` evaluation for an identifier and policy (`DEBUG`).
- **Decisions** -- allowed and denied requests (both `DEBUG`, keeping `INFO`/`WARN` free of per-request noise).
- **State handling** -- initial-state creation and expired-state detection in the stores (`DEBUG`).
- **Atomic persistence** -- Redis watch/transaction operations, `WATCH` conflicts and retries (`DEBUG`).

### Custom loggers

Implement the `Logger` interface to route output anywhere you like (SLF4J, Log4j, `java.util.logging`, etc.):

```java
Logger logger = (level, message) -> myLoggingFramework.log(level, message);
```

Both the service and the storage backend can share the same logger, or you can wire them independently:

```java
RateLimit<FixedWindowPolicy> rateLimit = RateLimit.build(
        Algorithm.fixedWindow(),
        Persistence.inMemory(serviceLogger),
        clock,
        serviceLogger
);
```

---

## Algorithms

### Fixed Window

Divides time into fixed intervals and counts requests within each window. Simple, memory-efficient, and ideal for low-traffic systems.

```java
FixedWindowPolicy policy = new FixedWindowPolicy(
        50,                     // limit
        Duration.ofMinutes(1)   // window size
);

RateLimitResult result = rateLimit.use("user-123", policy);
```

**Pros:** Simple implementation, minimal memory usage.
**Cons:** Allows burst traffic at window boundaries (up to 2x the limit).

---

### Sliding Window Log

Stores a timestamp log of each request. Provides exact precision by using a continuously sliding time window.

**Pros:** No burst problem at window edges, mathematically precise.
**Cons:** High memory consumption (stores every timestamp).

---

### Sliding Window Counter

A weighted combination of the current and previous fixed windows. Offers a good balance between precision and memory efficiency.

**Pros:** Much more accurate than Fixed Window, low memory footprint.
**Cons:** Approximation (assumes uniform request distribution within sub-windows).

---

### Token Bucket

Uses a bucket that fills with tokens at a constant rate. Each request consumes one token. Allows controlled bursts while maintaining an average rate.

```java
TokenBucketPolicy policy = new TokenBucketPolicy(
        10.0,    // bucket capacity (burst size)
        1.0      // refill rate (tokens per second)
);
```

**Pros:** Allows controlled bursts, fine-grained control, widely adopted (AWS, Stripe).
**Cons:** Requires tracking token count and refill timestamp.

---

### Leaky Bucket

Requests enter a bucket and are processed at a constant rate. Smooths traffic into an even flow, ideal for backends that cannot handle spikes.

**Pros:** Guarantees constant output rate, eliminates traffic spikes.
**Cons:** Does not allow bursts, may introduce processing delays.

---

### GCRA

Generic Cell Rate Algorithm. Uses a Theoretical Arrival Time (TAT) to determine if a request is allowed. Originally designed for ATM network traffic control.

```java
GcraPolicy policy = new GcraPolicy(
        1.0,                    // average rate (requests per second)
        Duration.ofSeconds(5)   // maximum burst (in time units)
);
```

**Pros:** Extreme precision with minimal state (single value), fits well on top of Redis transactions.
**Cons:** Higher conceptual complexity.

---

## Storage Backends

Each backend lives in its own Maven module and exposes a module-scoped `Persistence` factory. The rest of the API (`RateLimit`, `Algorithm`, policies) does not change.

### InMemory (`rate-limit-inmemory`)

A `ConcurrentHashMap`-based store (`io.github.lauto5.rateLimit.inmemory.Persistence`). Suitable for single-instance applications, development, and testing. State lives inside the JVM process.

```java
import io.github.lauto5.rateLimit.inmemory.Persistence;

RateLimit<FixedWindowPolicy> rateLimit = RateLimit.build(
        Algorithm.fixedWindow(),
        Persistence.inMemory()
);
```

Factories:
- `inMemory()` / `inMemory(logger)`. Run time is **Java 8+**; build time needs **JDK 9+** (the code is compiled with `--release 8`).

### Redis (`rate-limit-redis`)

A Redis-backed store (`io.github.lauto5.rateLimit.redis.Persistence`) for distributed rate limiting across multiple application instances sharing the same Redis server.

Consistency is guaranteed with the **`WATCH / MULTI / EXEC`** protocol: the store observes the key, the algorithm computes the new state in Java, and the transaction commits the serialized state with a TTL. If another process modified the observed key, the transaction aborts and the operation is retried a bounded number of times with jittered exponential backoff.

Persisted state uses a **versioned format** (`VersionedStateCodec`): a magic marker plus a format version prefix the payload. Corrupted or incompatible data is treated as absent (with a warning) and rewritten from scratch instead of failing.

```java
import io.github.lauto5.rateLimit.redis.Persistence;

RateLimit<FixedWindowPolicy> rateLimit = RateLimit.build(
        Algorithm.fixedWindow(),
        Persistence.inRedis("redis://localhost:6379")
);
```

Factories:
- `inRedis(url)` / `inRedis(url, logger)` -- use the default key namespace `rate-limit`.
- `inRedis(url, namespace)` / `inRedis(url, logger, namespace)` -- keys are written as
  `namespace + ":" + identifier`, so applications, environments, or versions sharing one
  Redis never collide.

Add the module as a dependency and provide a concrete SLF4J provider at runtime (e.g. `slf4j-simple`). The runtime contract is **Java 8+** (build needs **JDK 9+**).

---

## Architecture

The library follows **hexagonal architecture** (ports and adapters) to keep the core domain decoupled from infrastructure concerns. The reactor splits the layers into Maven modules:

```
                 +---------------------------------+
                 |  rate-limit-core                |
                 |  Public API (api/)              |
                 |  Algorithm, RateLimitResult     |
                 +----------------+----------------+
                                  |
                                  v
                 +---------------------------------+
                 |  Application Layer              |
                 |  (application/)                 |
                 |  - Service orchestration        |
                 |  - CorruptedStateException      |
                 |  - VersionedStateCodec          |
                 |  - Ports (contracts)            |
                 |  - Logging (Console/NoOp)       |
                 +--------+---------------+--------+
                          |               |
              +-----------v----+   +------v-----------------+
              |   Domain Core   |   |      Ports (out)      |
              |   (domain/)     |   |  AtomicOperation      |
              |  Algorithms     |   |  AtomicOperationResult |
              |  State / Policy |   |  Logger               |
              |  Codecs / Model |   |  RateLimitStore       |
              +-----------------+   |  StoreState           |
                                    +------------------------+
                                          |
                 +------------------------+------------------------+
                 |  rate-limit-inmemory / rate-limit-redis        |
                 |  Infrastructure adapters                       |
                 |  Persistence.inMemory() / Persistence.inRedis()|
                 +------------------------------------------------+
```

The dependency direction always points **inward**: infrastructure implements ports, the application layer depends on ports, and the domain core depends on nothing. `rate-limit-core` has no knowledge of Redis, Lettuce, or any concrete store.

**Key design decisions:**

- The `RateLimit` class is the only public entry point. It exposes a single `use()` method.
- Algorithms implement `RateLimitAlgorithm<S, P>` with typed state and policy generics.
- Storage backends implement `RateLimitStore` and guarantee atomicity via `executeAtomically()`.
- The state lifecycle (create, read, update, expiration) is fully managed by the library -- users never handle state directly.
- Persistence factories are module-specific (`io.github.lauto5.rateLimit.inmemory.Persistence`, `io.github.lauto5.rateLimit.redis.Persistence`) so unused infrastructure never leaks onto the classpath.

For a detailed explanation, see [ARCHITECTURE.md](docs/ARCHITECTURE.md).

---

## API Reference

### `RateLimit<P extends RateLimitPolicy>`

| Method | Description |
|---|---|
| `build(algorithm, store)` | Creates a rate limiter with default system clock and a silent logger |
| `build(algorithm, store, logger)` | Creates a rate limiter with default system clock and the given logger |
| `build(algorithm, store, clock)` | Creates a rate limiter with a custom clock (useful for testing) and a silent logger |
| `build(algorithm, store, clock, logger)` | Creates a rate limiter with a custom clock and the given logger |
| `use(identifier, policy)` | Evaluates a request against the given policy. Returns a `RateLimitResult` |

### `RateLimitResult`

| Method | Type | Description |
|---|---|---|
| `isAllowed()` | `boolean` | Whether the request is permitted |
| `getRemaining()` | `long` | Remaining requests in the current window/bucket |
| `getRetryAfter()` | `Optional<Duration>` | Time to wait before retrying (present only when denied) |
| `getResetAt()` | `Instant` | When the current window/bucket resets |

### Factory Classes

| Class | Module | Methods |
|---|---|---|
| `Algorithm` | core | `fixedWindow()`, `tokenBucket()`, `slidingWindowCounter()`, `slidingWindowLog()`, `leakyBucket()`, `gcra()` |
| `inmemory.Persistence` | in-memory | `inMemory()`, `inMemory(logger)` |
| `redis.Persistence` | redis | `inRedis(url)`, `inRedis(url, logger)`, `inRedis(url, namespace)`, `inRedis(url, logger, namespace)` |
| `ConsoleLogger` | core | `ConsoleLogger(clazz)`, `ConsoleLogger(clazz, level)`, `ConsoleLogger(name, level)` |

<!-- TODO: Add documentation for additional algorithms and persistence options as they are implemented -->

---

## Project Structure

```
rate-limit/
├── pom.xml                                  # Parent (aggregator) with dependencyManagement
├── rate-limit-core/
│   └── src/main/java/io/github/lauto5/rateLimit/
│       ├── RateLimit.java                   # Public entry point
│       ├── api/                             # Factory classes & result DTO
│       │   ├── Algorithm.java
│       │   └── RateLimitResult.java
│       ├── application/                     # Application layer
│       │   ├── CorruptedStateException.java
│       │   ├── RateLimitExecutor.java
│       │   ├── RateLimitService.java
│       │   ├── RateLimitAtomicOperation.java
│       │   ├── RateLimitResultMapper.java
│       │   ├── VersionedStateCodec.java
│       │   ├── logging/                     # Logger implementations
│       │   │   ├── ConsoleLogger.java       # Built-in console logger
│       │   │   └── NoOpLogger.java          # Default silent logger
│       │   └── ports/out/                   # Outbound contracts
│       │       ├── AtomicOperation.java
│       │       ├── AtomicOperationResult.java
│       │       ├── Logger.java
│       │       ├── RateLimitStore.java
│       │       └── StoreState.java
│       ├── domain/                          # Core domain
│       │   ├── algorithm/                   # Algorithm interfaces/impls & codecs
│       │   │   ├── StateCodec.java          # Serialization contract
│       │   │   └── PipeDelimitedCodec.java  # Pipe-delimited codec shared by states
│       │   ├── algorithmState/              # State value objects
│       │   ├── context/                     # Execution context
│       │   ├── model/                       # Decision & result models
│       │   └── policies/                    # Policy value objects
├── rate-limit-inmemory/
│   └── src/main/java/io/github/lauto5/rateLimit/
│       ├── infrastructure/InMemoryStore.java
│       └── inmemory/Persistence.java
├── rate-limit-redis/
│   └── src/main/java/io/github/lauto5/rateLimit/
│       ├── infrastructure/                  # Redis adapters
│       │   ├── LettuceTransactionPort.java
│       │   ├── RedisStore.java
│       │   ├── RedisTransactionPort.java
│       │   ├── TransactionBody.java
│       │   └── TransactionWrite.java
│       └── redis/Persistence.java
examples/
├── core-inmemory/                           # Example: core + in-memory store
└── core-redis/                              # Example: core + Redis store
```

---

## Building and Testing

```bash
mvn -f rate-limit/pom.xml install    # build and install the library modules
mvn -f rate-limit/pom.xml verify     # run tests (incl. Testcontainers integration tests) and coverage
```

Run an example:

```bash
mvn -f examples/core-inmemory compile exec:java
mvn -f examples/core-redis compile exec:java   # requires a Redis server
```

---

## Contributing

Contributions are welcome. Please see [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

---

## License

This project is licensed under the [MIT License](LICENSE).

Copyright (c) 2026 Lauto5