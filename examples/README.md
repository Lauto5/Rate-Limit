# Ejemplos de uso

Proyectos externos que consumen la libreria empaquetada, validando que el usuario
solamente necesita agregar la combinacion de dependencias que desea.

## Prerequisito

Instalar los modulos en el repositorio local de Maven:

```bash
mvn -f rate-limit/pom.xml install
```

## core + inmemory

Dependencias: `rate-limit-core` + `rate-limit-inmemory`. La persistencia vive en
memoria (un solo proceso, sin recursos externos).

```bash
mvn -f examples/core-inmemory compile exec:java
```

## core + redis

Dependencias: `rate-limit-core` + `rate-limit-redis` (+ `slf4j-simple` como
proveedor concreto de SLF4J que requiere Lettuce). La persistencia usa un
servidor Redis real; el estado se serializa con formato versionado y se actualiza
atomicamente con `WATCH / MULTI / EXEC`.

Requiere un servidor Redis accesible:

```bash
docker run -d --rm --name ratelimit-example-redis -p 6379:6379 redis:7-alpine
mvn -f examples/core-redis compile exec:java
```

Si el servidor escucha en otro host/puerto:

```bash
mvn -f examples/core-redis compile exec:java -Dredis.url=redis://localhost:6390
```