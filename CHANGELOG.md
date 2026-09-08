# Changelog

All notable changes to `io.github.lauto5:rate-limit` are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this
project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] - 2026-09-08

First stable release, published to Maven Central.

### Algorithms

- Fixed Window.
- Token Bucket.
- Sliding Window Counter.
- Sliding Window Log.
- Leaky Bucket.
- GCRA (Generic Cell Rate Algorithm).

### Modules

- `rate-limit-core` — public API, algorithms, policies, application layer and ports. No
  third-party runtime dependencies.
- `rate-limit-inmemory` — thread-safe in-memory persistence adapter for single-instance,
  development and testing scenarios.
- `rate-limit-redis` — distributed persistence adapter over Redis (Lettuce), with atomic
  operations per identifier via the `WATCH` / `MULTI` / `EXEC` protocol.

### Compatibility

- Runtime: Java 8+.
- Build: JDK 9+ (project compiles with `--release 8`).
- Build tool: Maven 3.9+.

### Installation

Maven Central, with a persistence module of your choice:

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

`rate-limit-redis` requires a concrete SLF4J provider at runtime (e.g. `slf4j-simple`).

### Example

```java
RateLimit<FixedWindowPolicy> rateLimit = RateLimit.build(
        Algorithm.fixedWindow(),
        Persistence.inMemory());

FixedWindowPolicy policy = new FixedWindowPolicy(100, Duration.ofMinutes(1));

RateLimitResult result = rateLimit.use("user-123", policy);
if (result.isAllowed()) {
    // Process the request
}
```

### Breaking changes

None — this is the first public release. No `0.x` versions were published to Maven Central.

### API stability

The public API (`api.Algorithm`, `api.RateLimitResult`, `RateLimit`, policy classes and the
`Persistence` factories) is the `1.0.0` contract and is versioned under Semantic Versioning.