# Usage examples

Standalone projects that consume the packaged library, validating that a user
only needs to add the combination of dependencies they want.

## Setup

Both projects share the `examples/pom.xml` parent, which centralizes the common
configuration (encoding, Java 8 target for the core examples, `exec-maven-plugin`,
`slf4j-simple` and surefire versions, the version of the library modules, and the
Spring Boot BOM for the spring-boot examples under a Java 17 target). The versions mirror
those of the main `rate-limit/pom.xml` parent: when you update them in the reactor, keep
them in sync here. To build both examples at once:

```bash
mvn -f examples/pom.xml package
```

## Prerequisite

Install the modules into the local Maven repository:

```bash
mvn -f rate-limit/pom.xml install
```

## core + inmemory

Dependencies: `rate-limit-core` + `rate-limit-inmemory`. Persistence lives in
memory (single process, no external resources).

```bash
mvn -f examples/core-inmemory compile exec:java
```

## core + redis

Dependencies: `rate-limit-core` + `rate-limit-redis` (+ `slf4j-simple` as the
concrete SLF4J provider required by Lettuce). Persistence uses a real Redis
server; state is serialized in a versioned format and updated atomically with
`WATCH / MULTI / EXEC`.

Requires a reachable Redis server:

```bash
docker run -d --rm --name ratelimit-example-redis -p 6379:6379 redis:7-alpine
mvn -f examples/core-redis compile exec:java
```

If the server listens on another host/port:

```bash
mvn -f examples/core-redis compile exec:java -Dredis.url=redis://localhost:6390
```

## spring boot + inmemory

Dependencies: `rate-limit-core` + `rate-limit-inmemory` + `rate-limit-spring-boot`
(+ Spring Boot, transitivo). Validates the distribution contract of the Spring Boot
integration: the auto-configuration provisions the store and `Clock` from
`application.yml`, and the application builds its own `RateLimit<FixedWindowPolicy>`
bean. Requires Java 17+.

```bash
mvn -f examples/spring-boot-inmemory compile exec:java
```

## spring boot + redis

Dependencies: `rate-limit-core` + `rate-limit-redis` + `rate-limit-spring-boot`.
SLF4J provider comes from Spring Boot itself (Logback, transitive). The same contract,
validated against a real Redis; the tests use Testcontainers.

Requires a reachable Redis server:

```bash
docker run -d --rm --name ratelimit-example-redis -p 6379:6379 redis:7-alpine
mvn -f examples/spring-boot-redis compile exec:java
```

The tests (`mvn verify`) spin their own Redis container, so no manual setup is needed.
The Redis URL is overridable via the `RATE_LIMIT_REDIS_URL` environment variable.