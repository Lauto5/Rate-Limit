# Rate Limit

[![Maven Central](https://img.shields.io/maven-central/v/io.github.lauto5/rate-limit-core.svg)](https://central.sonatype.com/artifact/io.github.lauto5/rate-limit-core/1.1.0)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Java](https://img.shields.io/badge/Java-8+-blue.svg)](https://www.oracle.com/java/)

A lightweight, extensible rate limiting library for Java built on hexagonal architecture.
It exposes a clean, type-safe API, supports six industry-standard algorithms and ships with
pluggable persistence that lives in separate Maven modules — so each application only depends
on the storage it actually uses.

---

## Table of Contents

- [What is Rate Limit?](#what-is-rate-limit)
- [Features](#features)
- [Supported Algorithms](#supported-algorithms)
- [Installation](#installation)
- [Quick Start](#quick-start)
- [Persistence](#persistence)
- [Spring Boot](#spring-boot)
- [Redis](#redis)
- [API](#api)
- [Examples](#examples)
- [Architecture](#architecture)
- [Testing](#testing)
- [Compatibility](#compatibility)
- [CI/CD & Releases](#cicd--releases)
- [Contributing](#contributing)
- [License](#license)

---

## What is Rate Limit?

Rate Limit enforces request quotas per identifier (a user, an API key, an IP address, ...).
You define a **policy** (how many requests are allowed over which period), and each call
answers one question: *is this request allowed, and what is the remaining quota?*

The library is split into modules so you only pull in the persistence you need:

| Module | What it provides | Runtime |
|---|---|---|
| `rate-limit-core` | Public API, algorithms, policies, application layer and ports | Java 8+ |
| `rate-limit-inmemory` | Thread-safe in-process store (`Persistence.inMemory()`) | Java 8+ |
| `rate-limit-redis` | Distributed store over Redis/Lettuce (`Persistence.inRedis(...)`) | Java 8+ |
| `rate-limit-spring-boot` | Spring Boot auto-configuration (store + `Clock` beans) | Java 17+ / Spring Boot 3.x |

---

## Features

- **Six algorithms** — Fixed Window, Sliding Window Log, Sliding Window Counter, Token Bucket,
  Leaky Bucket and GCRA, each with its own strongly-typed policy class.
- **Module-based, pluggable storage** — swap backends without touching application logic.
  In-memory for single instances, Redis for distributed limits.
- **Hexagonal architecture** — domain logic is decoupled from infrastructure. No framework
  coupling; `core` has zero third-party runtime dependencies.
- **Type-safe API** — the compiler prevents mixing a policy with the wrong algorithm.
- **Atomic per identifier** — concurrent requests for the same identifier are serialized at the
  store, preventing lost updates. Redis uses `WATCH / MULTI / EXEC`.
- **Thread-safe** — safe for concurrent use out of the box.
- **Pluggable logging** — inject your own `Logger`; silent by default, with a `ConsoleLogger`
  included.
- **Spring Boot integration** — the infrastructure (`RateLimitStore`, `Clock`) is provisioned
  from `application.yml`; you keep using the core API.

---

## Supported Algorithms

| Algorithm | Precision | Memory | Burst Support |
|---|---|---|---|
| Fixed Window | Medium | Low | No |
| Sliding Window Log | High | High | No |
| Sliding Window Counter | High | Low | No |
| Token Bucket | High | Medium | Yes |
| Leaky Bucket | Medium | Medium | No |
| GCRA | High | Low | Yes |

Each algorithm has its own tutorial under [docs/algorithms](docs/algorithms/README.md).

---

## Installation

The `1.1.x` artifacts are **published on Maven Central** (`io.github.lauto5`). Add
`rate-limit-core` plus the persistence module you need. The Spring Boot module ships with the
project's next release (see [PUBLISHING.md](PUBLISHING.md)).

### In-memory (single instance) — Maven

```xml
<dependency>
    <groupId>io.github.lauto5</groupId>
    <artifactId>rate-limit-core</artifactId>
    <version>1.1.0</version>
</dependency>
<dependency>
    <groupId>io.github.lauto5</groupId>
    <artifactId>rate-limit-inmemory</artifactId>
    <version>1.1.0</version>
</dependency>
```

### Redis (distributed) — Maven

```xml
<dependency>
    <groupId>io.github.lauto5</groupId>
    <artifactId>rate-limit-core</artifactId>
    <version>1.1.0</version>
</dependency>
<dependency>
    <groupId>io.github.lauto5</groupId>
    <artifactId>rate-limit-redis</artifactId>
    <version>1.1.0</version>
</dependency>
```

> `rate-limit-redis` uses Lettuce and therefore needs a concrete SLF4J provider at runtime
> (for example `slf4j-simple`). The in-memory module has no third-party runtime dependencies.

### Spring Boot — Maven

```xml
<dependency>
    <groupId>io.github.lauto5</groupId>
    <artifactId>rate-limit-core</artifactId>
    <version>1.1.0</version>
</dependency>
<dependency>
    <groupId>io.github.lauto5</groupId>
    <artifactId>rate-limit-inmemory</artifactId>   <!-- or rate-limit-redis -->
    <version>1.1.0</version>
</dependency>
<dependency>
    <groupId>io.github.lauto5</groupId>
    <artifactId>rate-limit-spring-boot</artifactId>
    <version>1.1.0</version>
</dependency>
```

### Gradle

```groovy
implementation 'io.github.lauto5:rate-limit-core:1.0.0'
implementation 'io.github.lauto5:rate-limit-inmemory:1.0.0'
```

---

## Quick Start

Five minutes, one algorithm, one adapter, no configuration:

```java
import io.github.lauto5.rateLimit.RateLimit;
import io.github.lauto5.rateLimit.api.Algorithm;
import io.github.lauto5.rateLimit.api.RateLimitResult;
import io.github.lauto5.rateLimit.domain.policies.FixedWindowPolicy;
import io.github.lauto5.rateLimit.inmemory.Persistence;

import java.time.Duration;

// 1. Build the rate limiter (typed: it works with FixedWindowPolicy only)
RateLimit<FixedWindowPolicy> rateLimit = RateLimit.build(
        Algorithm.fixedWindow(),
        Persistence.inMemory()
);

// 2. Define a policy: 100 requests per minute
FixedWindowPolicy policy = new FixedWindowPolicy(
        100,                    // max 100 requests
        Duration.ofMinutes(1)   // per minute
);

// 3. Check every request
RateLimitResult result = rateLimit.use("user-123", policy);

if (result.isAllowed()) {
    System.out.println("Remaining: " + result.getRemaining());
} else {
    System.out.println("Retry after: " + result.getRetryAfter().orElse(null));
    System.out.println("Resets at:  " + result.getResetAt());
}
```

That's the whole API surface you need for most use cases. For the full walkthrough, see
[docs/getting-started.md](docs/getting-started.md).

---

## Persistence

Rate limiting requires shared, atomic state. The store is the persistence port of the library.

- **[In-memory](docs/persistence/in-memory.md)** — `Persistence.inMemory()`. State lives inside
  the JVM. Correct for single-instance applications, development and tests.
- **[Redis](docs/persistence/redis.md)** — `Persistence.inRedis(...)`. All instances read and
  update the same state, enforcing a global limit. State is serialized with a versioned codec
  and updated atomically with `WATCH / MULTI / EXEC`.

You can also implement `RateLimitStore` yourself and keep the same algorithms untouched.

---

## Spring Boot

`rate-limit-spring-boot` is a thin auto-configuration, **not** a full starter: it provisions a
`RateLimitStore` (in-memory or Redis, selected in `application.yml`) and a `Clock` as Spring
beans. You still build your own strongly-typed `RateLimit` with the core API:

```java
@Bean
RateLimit<FixedWindowPolicy> rateLimit(RateLimitStore store) {
    return RateLimit.build(Algorithm.fixedWindow(), store);
}
```

```yaml
rate-limit:
  persistence:
    type: inmemory   # or "redis"
```

Requires **Java 17+** and **Spring Boot 3.x**. Full guide:
[docs/integrations/spring-boot.md](docs/integrations/spring-boot.md).

---

## Redis

For distributed rate limiting across application instances sharing one Redis server:

```java
import io.github.lauto5.rateLimit.redis.Persistence;

RateLimit<FixedWindowPolicy> rateLimit = RateLimit.build(
        Algorithm.fixedWindow(),
        Persistence.inRedis("redis://localhost:6379")
);
```

- **Atomicity** — `WATCH / MULTI / EXEC`; conflicts are retried with bounded jittered backoff.
- **Namespaces** — keys are `namespace + ":" + identifier` (default `rate-limit`), so
  applications sharing a Redis never collide.
- **Expiration** — each key is set with a TTL derived from the algorithm's window.
- **Serialization** — versioned payload format; corrupt or foreign data is rebuilt from scratch.

Details and resilience semantics: [docs/persistence/redis.md](docs/persistence/redis.md).

---

## API

### `RateLimit<P extends RateLimitPolicy>`

| Method | Description |
|---|---|
| `build(algorithm, store)` | Creates a rate limiter with the system clock and a silent logger |
| `build(algorithm, store, logger)` | Same, with the given logger |
| `build(algorithm, store, clock)` | Same, with a custom clock (deterministic testing) |
| `build(algorithm, store, clock, logger)` | Same, with custom clock and logger |
| `use(identifier, policy)` | Evaluates a request. Returns a `RateLimitResult` |

### `RateLimitResult`

| Method | Type | Description |
|---|---|---|
| `isAllowed()` | `boolean` | Whether the request is permitted |
| `getRemaining()` | `long` | Remaining requests in the current window/bucket |
| `getRetryAfter()` | `Optional<Duration>` | Time to wait before retrying (present when denied) |
| `getResetAt()` | `Instant` | When the current window/bucket resets |

### Factory classes

| Class | Module | Methods |
|---|---|---|
| `Algorithm` | core | `fixedWindow()`, `tokenBucket()`, `slidingWindowCounter()`, `slidingWindowLog()`, `leakyBucket()`, `gcra()` |
| `inmemory.Persistence` | in-memory | `inMemory()`, `inMemory(logger)` |
| `redis.Persistence` | redis | `inRedis(url)`, `inRedis(url, logger)`, `inRedis(url, namespace)`, `inRedis(url, logger, namespace)` |
| `ConsoleLogger` | core | `ConsoleLogger(clazz)`, `ConsoleLogger(clazz, level)`, `ConsoleLogger(name, level)` |

---

## Examples

Standalone consumer projects live under [`examples/`](examples/README.md). They validate the
distribution contract from *outside* the reactor.

```bash
mvn -f rate-limit/pom.xml install                      # build + install the library

mvn -f examples/core-inmemory compile exec:java        # core + in-memory
mvn -f examples/core-redis compile exec:java           # core + Redis (needs a Redis server)
mvn -f examples/spring-boot-inmemory compile exec:java # Spring Boot + in-memory (Java 17)
mvn -f examples/spring-boot-redis compile exec:java    # Spring Boot + Redis (needs a Redis server)
```

---

## Architecture

The library follows **hexagonal architecture**: `core` holds the public API, the application
layer and the domain (algorithms, policies, codecs); the store modules are infrastructure
adapters that implement the `RateLimitStore` port. The dependency direction always points
inward — `core` doesn't know about Redis, Lettuce or Spring.

For the full internal design (state lifecycle, atomicity, expiration, optimistic locking and
the reasoning behind each decision), see [docs/architecture.md](docs/architecture.md).

---

## Testing

Unit, integration and concurrency tests run with `mvn verify` (the Redis tests use
Testcontainers and need Docker):

```bash
mvn -B -f rate-limit/pom.xml clean verify     # library reactor (tests + JaCoCo)
mvn -B -f examples/pom.xml clean verify       # consumer examples against installed artifacts
```

---

## Compatibility

| Aspect | Requirement |
|---|---|
| Runtime (core / inmemory / redis) | Java 8+ |
| Runtime (spring-boot) | Java 17+ and Spring Boot 3.x |
| Build | JDK 17+ and Maven 3.9+ (modules compile with `--release 8`, spring-boot with `--release 17`) |
| Redis integration tests | Docker (Testcontainers) |

---

## CI/CD & Releases

- **CI** (`.github/workflows/ci.yml`) — builds and tests the reactor and the examples on
  every PR and push to `main` (Java 17 and 21).
- **Release** (`.github/workflows/release.yml`) — a `vX.Y.Z` tag publishes signed artifacts to
  Maven Central automatically, after validating the tag matches the reactor version.
- **Dry run** (`.github/workflows/release-dry-run.yml`) — validates the full pipeline without
  touching Central or production secrets.

How to release a new version: [PUBLISHING.md](PUBLISHING.md).

---

## Contributing

Contributions are welcome. See [CONTRIBUTING.md](CONTRIBUTING.md).

---

## License

This project is licensed under the [MIT License](LICENSE).

Copyright (c) 2026 Lauto5
