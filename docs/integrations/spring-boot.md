# Spring Boot integration

`rate-limit-spring-boot` provisions the library's infrastructure as Spring beans so your
application connects rate limiting declaratively. It is a **thin auto-configuration**, not a
full starter.

## The integration model (V1)

```text
Spring Boot
    │
    ├── Clock            (bean, Clock.systemUTC())
    ├── RateLimitStore   (bean)
    │      ├── InMemory  (when rate-limit-inmemory is on the classpath)
    │      └── Redis     (when rate-limit-redis is on the classpath)
    │
    ▼
Application code
    │
    └── RateLimit.build(...)   ← your strongly typed RateLimit<P>
```

Two explicit decisions:

- Spring **never creates a `RateLimit` bean** and never picks an algorithm or policy from YAML.
  The algorithm is a core domain concern: you keep the typed API and build your own
  `RateLimit<P>`.
- Spring does not know an algorithm; it only wires infrastructure. This keeps the module small
  and the domain free of the framework.

## Requirements

- **Java 17+**
- **Spring Boot 3.x**
- One persistence adapter on the classpath (`rate-limit-inmemory` or `rate-limit-redis`), or a
  `RateLimitStore` bean of your own.

## Installation

> The module ships with the project's next Maven Central release. Until then, install the
> reactor locally (`mvn -f rate-limit/pom.xml install`) to consume it.

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
<dependency>
    <groupId>io.github.lauto5</groupId>
    <artifactId>rate-limit-spring-boot</artifactId>
    <version>1.1.0</version>
</dependency>
```

For Redis, swap `rate-limit-inmemory` for `rate-limit-redis` and add a concrete SLF4J provider
(Spring Boot brings Logback, so no extra step is needed there). If you add **only**
`rate-limit-spring-boot` without an adapter, no `RateLimitStore` bean is created
(`@ConditionalOnClass` guard) — that is intentional.

## Configuration

```yaml
rate-limit:
  enabled: true              # default: true; set false to disable entirely
  persistence:
    type: inmemory           # default: inmemory, or "redis"
    redis:
      url: redis://localhost:6379
      namespace: my-app      # default: rate-limit
```

| Property | Default | Meaning |
|---|---|---|
| `rate-limit.enabled` | `true` | Master switch of the auto-configuration |
| `rate-limit.persistence.type` | `inmemory` | Which adapter is provisioned (`inmemory` / `redis`) |
| `rate-limit.persistence.redis.url` | — | Redis connection URL (required for `type: redis`) |
| `rate-limit.persistence.redis.namespace` | `rate-limit` | Key prefix in Redis |

## Beans provided

| Bean | Condition | Notes |
|---|---|---|
| `RateLimitStore` (in-memory) | `rate-limit-inmemory` present and `type=inmemory` (default) | Thread-safe, single process |
| `RateLimitStore` (Redis) | `rate-limit-redis` present and `type=redis` | `destroyMethod="close"` → closed on context shutdown |
| `Clock` | always | `Clock.systemUTC()` |

Every bean is `@ConditionalOnMissingBean`: a bean you define yourself always wins, so you can
override the store or the clock. If the configured `type` has no matching adapter on the
classpath, **no** `RateLimitStore` bean is created — the failure is a missing bean at injection
time, not a cryptic startup error.

## Usage

Inject the store and build your own `RateLimit`:

```java
import java.time.Duration;

import io.github.lauto5.rateLimit.RateLimit;
import io.github.lauto5.rateLimit.api.Algorithm;
import io.github.lauto5.rateLimit.api.RateLimitResult;
import io.github.lauto5.rateLimit.application.ports.out.RateLimitStore;
import io.github.lauto5.rateLimit.domain.policies.FixedWindowPolicy;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RateLimitConfig {

    @Bean
    RateLimit<FixedWindowPolicy> rateLimit(RateLimitStore store) {
        return RateLimit.build(Algorithm.fixedWindow(), store);
    }
}
```

```java
import io.github.lauto5.rateLimit.RateLimit;
import io.github.lauto5.rateLimit.api.RateLimitResult;
import io.github.lauto5.rateLimit.domain.policies.FixedWindowPolicy;

import java.time.Duration;

import org.springframework.stereotype.Service;

@Service
public class MyService {

    private final RateLimit<FixedWindowPolicy> rateLimit;

    public MyService(RateLimit<FixedWindowPolicy> rateLimit) {
        this.rateLimit = rateLimit;
    }

    public void call(String userId) {
        RateLimitResult result = rateLimit.use(userId,
                new FixedWindowPolicy(100, Duration.ofMinutes(1)));
        // ...
    }
}
```

Overriding the store or clock is declarative — just define your own bean:

```java
@Bean
RateLimitStore myStore() {                 // wins over the auto-configured one
    return myCustomStore;
}

@Bean
Clock mockClock() {                        // wins over Clock.systemUTC()
    return Clock.fixed(...);
}
```

## Verified consumer projects

Standalone consumers (outside the reactor) validate this contract in
[`examples/`](https://github.com/Lauto5/Rate-Limit/tree/main/examples):
`spring-boot-inmemory` and `spring-boot-redis`, including a Redis lifecycle test that asserts
the store's `destroyMethod` is `"close"`.

## Out of scope in V1

- `@RateLimited` / AOP / SpEL annotations on methods.
- Micrometer observability.
- Per-algorithm YAML configuration.

These are documented as future work; the integration deliberately does not invent them.

## Further reading

- Framework-free usage: [getting-started.md](../getting-started.md)
- Under the hood: [architecture.md](../architecture.md)
