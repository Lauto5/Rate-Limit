# Rate Limit

<!-- TODO: Add badges once published -->
<!-- [![Maven Central](https://img.shields.io/maven-central/v/io.github.lauto5/rate-limit.svg)](https://search.maven.org/search?q=g:io.github.lauto5%20AND%20a:rate-limit) -->
<!-- [![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT) -->
<!-- [![Java](https://img.shields.io/badge/Java-17+-blue.svg)](https://www.oracle.com/java/) -->

A lightweight, extensible rate limiting library for Java applications built on hexagonal architecture principles.

Rate Limit provides a clean, type-safe API for enforcing request quotas using multiple algorithms, with pluggable storage backends and zero dependencies on external frameworks.

---

## Table of Contents

- [Features](#features)
- [Supported Algorithms](#supported-algorithms)
- [Requirements](#requirements)
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
- **Pluggable storage** -- Swap storage backends without changing application logic. Ships with an in-memory store and a Redis-backed store.
- **Hexagonal architecture** -- Clean separation between domain logic and infrastructure. No framework coupling.
- **Type-safe configuration** -- Each algorithm has its own strongly-typed policy class. No magic strings or generic configuration maps.
- **Thread-safe** -- Designed for concurrent environments out of the box.
- **Pluggable logging** -- Inject your own `Logger` at build time to emit diagnostic output across the whole pipeline. Silent by default, with a `ConsoleLogger` included.
- **Zero external dependencies** -- Only requires the Java standard library at runtime. JUnit 5 is used for testing.

---

## Supported Algorithms

| Algorithm | Precision | Memory | Burst Support | Status |
|---|---|---|---|---|
| Fixed Window | Medium | Low | No | Implemented |
| Sliding Window Log | High | High | No | Planned |
| Sliding Window Counter | High | Low | No | Planned |
| Token Bucket | High | Medium | Yes | Planned |
| Leaky Bucket | Medium | Medium | No | Planned |
| GCRA | High | Low | Yes | Planned |

---

## Requirements

- **Java** 8 or later to run (the build compiles with `--release 8`, so a JDK 9+ is required to build)
- **Maven** 3.9+

---

## Installation

### Maven

```xml
<dependency>
    <groupId>io.github.lauto5</groupId>
    <artifactId>rate-limit</artifactId>
    <version><!-- TODO: Add stable version once released (e.g. 1.0.0) --></version>
</dependency>
```

### Gradle

```groovy
implementation 'io.github.lauto5:rate-limit:<!-- TODO: Add stable version -->'
```

---

## Quick Start

```java
import io.github.lauto5.rateLimit.RateLimit;
import io.github.lauto5.rateLimit.api.Algorithm;
import io.github.lauto5.rateLimit.api.Persistence;
import io.github.lauto5.rateLimit.api.RateLimitResult;
import io.github.lauto5.rateLimit.domain.policies.FixedWindowPolicy;

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

Rate Limit exposes a pluggable `Logger` port (`application/ports/out/Logger`) with `DEBUG`, `INFO`, `WARN`, and `ERROR` levels. By default the library is **silent** (a `NoOpLogger` is used). To opt in, inject a logger via the `build` overloads:

```java
import io.github.lauto5.rateLimit.RateLimit;
import io.github.lauto5.rateLimit.api.Algorithm;
import io.github.lauto5.rateLimit.api.Persistence;
import io.github.lauto5.rateLimit.infraestructure.ConsoleLogger;
import io.github.lauto5.rateLimit.domain.policies.FixedWindowPolicy;

// The built-in console logger emits INFO+ by default
ConsoleLogger logger = new ConsoleLogger(RateLimit.class);

RateLimit<FixedWindowPolicy> rateLimit = RateLimit.build(
        Algorithm.fixedWindow(),
        Persistence.inMemory(),
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
- **Decisions** -- allowed requests (`INFO`) and denied requests with `retryAfter` (`WARN`).
- **State handling** -- initial-state creation and expired-state detection in the stores (`DEBUG`).
- **Atomic persistence** -- Redis key/value operations, CAS conflicts and retries (`DEBUG`).

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

<!-- TODO: Algorithm implementation not yet available -->

Stores a timestamp log of each request. Provides exact precision by using a continuously sliding time window.

**Pros:** No burst problem at window edges, mathematically precise.
**Cons:** High memory consumption (stores every timestamp).

---

### Sliding Window Counter

<!-- TODO: Algorithm implementation not yet available -->

A weighted combination of the current and previous fixed windows. Offers a good balance between precision and memory efficiency.

**Pros:** Much more accurate than Fixed Window, low memory footprint.
**Cons:** Approximation (assumes uniform request distribution within sub-windows).

---

### Token Bucket

<!-- TODO: Algorithm implementation not yet available -->

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

<!-- TODO: Algorithm implementation not yet available -->

Requests enter a bucket and are processed at a constant rate. Smooths traffic into an even flow, ideal for backends that cannot handle spikes.

**Pros:** Guarantees constant output rate, eliminates traffic spikes.
**Cons:** Does not allow bursts, may introduce processing delays.

---

### GCRA

<!-- TODO: Algorithm implementation not yet available -->

Generic Cell Rate Algorithm. Uses a Theoretical Arrival Time (TAT) to determine if a request is allowed. Originally designed for ATM network traffic control.

```java
GcraPolicy policy = new GcraPolicy(
        1.0,                    // average rate (requests per second)
        Duration.ofSeconds(5)   // maximum burst (in time units)
);
```

**Pros:** Extreme precision with minimal state (single value), native Redis atomicity support.
**Cons:** Higher conceptual complexity.

---

## Storage Backends

### InMemoryStore

A `ConcurrentHashMap`-based store included in the library. Suitable for single-instance applications, development, and testing.

```java
RateLimit<FixedWindowPolicy> rateLimit = RateLimit.build(
        Algorithm.fixedWindow(),
        Persistence.inMemory()
);
```

### Redis Store

A Redis-backed store that uses a compare-and-swap protocol with atomic retries to provide consistency across processes sharing the same Redis instance. Enables distributed rate limiting across multiple application instances.

```java
RateLimit<FixedWindowPolicy> rateLimit = RateLimit.build(
        Algorithm.fixedWindow(),
        Persistence.inRedis("redis://localhost:6379")
);

// Close it when no longer needed:
// ((AutoCloseable) rateLimitStore).close();
```

---

## Architecture

The library follows **hexagonal architecture** (ports and adapters) to keep the core domain decoupled from infrastructure concerns.

```
                    +--------------------------+
                    |        Public API        |
                    |  RateLimit / Algorithm / |
                    |      Persistence         |
                    +------------+-------------+
                                 |
                    +------------v-------------+
                    |     Application Layer     |
                    |  RateLimitService         |
                    |  RateLimitAtomicOperation |
                    |  RateLimitResultMapper    |
                    +--+------------------+----+
                       |                  |
              +--------v------+  +--------v--------+
              |  Domain Core   |  |  Ports (in/out) |
              |  Algorithm     |  |  Store           |
              |  State         |  |  Result          |
              |  Policy        |  |  Operation       |
              +----------------+  +--------+--------+
                                           |
                                  +--------v--------+
                                  |   Infrastructure |
                                  |   InMemoryStore  |
                                  |   (Redis, etc.)  |
                                  +-----------------+
```

**Key design decisions:**

- The `RateLimit` class is the only public entry point. It exposes a single `use()` method.
- Algorithms implement `RateLimitAlgorithm<S, P>` with typed state and policy generics.
- Storage backends implement `RateLimitStore` and guarantee atomicity via `executeAtomically()`.
- The state lifecycle (create, read, update, expiration) is fully managed by the library -- users never handle state directly.

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

| Class | Methods |
|---|---|
| `Algorithm` | `fixedWindow()`, `tokenBucket()`, `slidingWindowCounter()`, `slidingWindowLog()`, `leakyBucket()`, `gcra()` |
| `Persistence` | `inMemory()`, `inMemory(logger)`, `inRedis(url)`, `inRedis(url, logger)` |
| `ConsoleLogger` | `ConsoleLogger(clazz)`, `ConsoleLogger(clazz, level)`, `ConsoleLogger(name, level)` |

<!-- TODO: Add documentation for additional algorithms and persistence options as they are implemented -->

---

## Project Structure

```
rate-limit/
├── pom.xml
└── src/
    ├── main/java/io/github/lauto5/rateLimit/
    │   ├── RateLimit.java                     # Public entry point
    │   ├── api/                               # Factory classes
    │   │   ├── Algorithm.java
    │   │   ├── Persistence.java
    │   │   └── RateLimitResult.java
    │   ├── application/                       # Application layer
    │   │   ├── RateLimitExecutor.java
    │   │   ├── RateLimitService.java
    │   │   ├── RateLimitAtomicOperation.java
    │   │   ├── RateLimitResultMapper.java
    │   │   └── ports/
    │   │       └── out/                       # Outbound contracts
    │   │           ├── AtomicOperation.java
    │   │           ├── AtomicOperationResult.java
    │   │           ├── KeyValueStorePort.java
    │   │           ├── Logger.java
    │   │           ├── RateLimitStore.java
    │   │           ├── StateCodec.java
    │   │           └── StoreState.java
    │   ├── domain/                            # Core domain
    │   │   ├── algorithm/                     # Algorithm interfaces & implementations
    │   │   ├── algorithmState/                # State value objects
    │   │   ├── context/                       # Execution context
    │   │   ├── model/                         # Decision & result models
    │   │   └── policies/                      # Policy value objects
    │   └── infraestructure/                   # Infrastructure adapters
    │       ├── ConsoleLogger.java             # Built-in console logger
    │       ├── InMemoryStore.java
    │       ├── LettuceKeyValueStore.java
    │       ├── NoOpLogger.java                # Default silent logger
    │       └── RedisStore.java
    └── test/java/io/github/lauto5/rateLimit/  # Test suite
```

---

## Contributing

Contributions are welcome. Please see [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

---

## License

This project is licensed under the [MIT License](LICENSE).

Copyright (c) 2026 Lauto5
