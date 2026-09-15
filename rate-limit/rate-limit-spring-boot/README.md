# rate-limit-spring-boot

Spring Boot integration for the Rate Limit library. This module is an **auto-configuration**,
not a complete starter: it provisions the persistence adapter (`RateLimitStore`) and a `Clock`
as Spring beans. The consumer keeps using the core domain API directly.

## Requirements

- Java 17+
- Spring Boot 3.x

## Installation

This module does **not** bundle a persistence adapter. It declares `rate-limit-inmemory` and
`rate-limit-redis` as optional dependencies, so the consumer must declare the adapter it wants:

```xml
<!-- InMemory: core + inmemory + spring-boot -->
<dependency>
    <groupId>io.github.lauto5</groupId>
    <artifactId>rate-limit-core</artifactId>
    <version>...</version>
</dependency>
<dependency>
    <groupId>io.github.lauto5</groupId>
    <artifactId>rate-limit-inmemory</artifactId>
    <version>...</version>
</dependency>
<dependency>
    <groupId>io.github.lauto5</groupId>
    <artifactId>rate-limit-spring-boot</artifactId>
    <version>...</version>
</dependency>

<!-- Redis: core + redis + spring-boot (instead of inmemory) -->
```

If you only add `rate-limit-spring-boot` without an adapter, no `RateLimitStore` bean is
created (the auto-configuration is guarded by `@ConditionalOnClass`). This is intentional: the
integration never assumes a specific adapter. Add the adapter you need, or define your own
`RateLimitStore` bean.

## Configuration

```yaml
rate-limit:
  enabled: true            # default: true
  persistence:
    type: inmemory         # default: inmemory (or "redis")
    redis:
      url: redis://localhost:6379
      namespace: my-app    # default: rate-limit
```

## Beans provided

| Bean | Condition | Notes |
|---|---|---|
| `RateLimitStore` (in-memory) | `rate-limit-inmemory` on classpath and `persistence.type=inmemory` (default) | Thread-safe, single-process |
| `RateLimitStore` (Redis) | `rate-limit-redis` on classpath and `persistence.type=redis` | `destroyMethod="close"` |
| `Clock` | always | `Clock.systemUTC()` |

Every bean is `@ConditionalOnMissingBean`: a bean you define yourself always wins.

## Usage

Spring provisions the store; the consumer builds its own strongly typed `RateLimit`:

```java
@Configuration
public class RateLimitConfig {

    @Bean
    RateLimit<FixedWindowPolicy> rateLimit(RateLimitStore store) {
        return RateLimit.build(Algorithm.fixedWindow(), store);
    }
}
```

```java
@Service
public class MyService {

    private final RateLimit<FixedWindowPolicy> rateLimit;

    public MyService(RateLimit<FixedWindowPolicy> rateLimit) {
        this.rateLimit = rateLimit;
    }

    public void execute(String userId) {
        RateLimitResult result = rateLimit.use(userId,
                new FixedWindowPolicy(100, Duration.ofMinutes(1)));
        // ...
    }
}
```

The integration does not create `RateLimit<?, ?>` beans and does not select the algorithm:
those are core concerns, decided by the consumer.

## Out of scope

`@RateLimited` / AOP, Micrometer observability and algorithm configuration in YAML are
documented as future work, not part of this module (see
`docs-for-agent-ia/feature-springboot/spring-boot-integration-opencode.md`).