# Usage examples

Standalone projects that consume the packaged library, validating that a user
only needs to add the combination of dependencies they want.

## Setup

Both projects share the `examples/pom.xml` parent, which centralizes the common
configuration (encoding, Java 8 target, `exec-maven-plugin` and `slf4j-simple`
versions, and the versions of the library modules). The versions mirror those of
the main `rate-limit/pom.xml` parent: when you update them in the reactor, keep
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