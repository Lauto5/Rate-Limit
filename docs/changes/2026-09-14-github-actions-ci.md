# CI con GitHub Actions — build, tests y cobertura del reactor

| | |
|---|---|
| **Date** | 2026-09-14 |
| **Type** | Chore |
| **Module(s)** | CI (`.github/workflows/ci.yml`) |
| **Status** | Not merged — branch `feature/github-actions`. |
| **Branch** | `feature/github-actions` |
| **Ticket / issue** | `N/A` |
| **Author(s)** | `N/A` (opencode-assisted session) |

## Summary

Añade el workflow `ci.yml` que ejecuta el build y los tests del proyecto en GitHub
Actions: compila y testea el reactor `rate-limit/` (con los tests de integración de Redis
vía Testcontainers) y luego compila los `examples/` contra los artifacts instalados, en
una matriz de JDK 17 y 21, subiendo el reporte de cobertura JaCoCo como artifact del run.

## Motivation

El proyecto no tenía ningún pipeline de integración continua. Con `1.0.0` publicado en
Maven Central, hace falta una verificación automática por cada push a `main` y por cada
pull request para detectar a tiempo regresiones en el reactor, en los tests (incluida la
integración con Redis) y en los ejemplos consumidores.

## What changed

- **Workflow nuevo** `.github/workflows/ci.yml`:
  - triggers: `push` a `main` y `pull_request` (con `concurrency` y `cancel-in-progress`).
  - matriz de JDK **Temurin 17 y 21** sobre `ubuntu-latest` (el build exige JDK 9+ por el
    flag `--release 8`; el bytecode generado es Java 8).
  - `mvn -B -f rate-limit/pom.xml clean install`: compila, corre todos los tests (incluye
    `Testcontainers`/redis usando el Docker del runner), genera el reporte JaCoCo e
    instala los artifacts locales para los ejemplos.
  - `mvn -B -f examples/pom.xml clean verify`: compila los ejemplos contra los artifacts
    instalados (el ejemplo de redis no se ejecuta, solo compila, para no exigir Redis vivo).
  - `actions/upload-artifact` sube los reportes JaCoCo de `rate-limit-core`,
    `rate-limit-inmemory` y `rate-limit-redis` (14 días de retención).
  - `actions/setup-java` con `cache: maven` para reutilizar el repositorio local.

## What did NOT change

- No se añade automatización de release (Stage 8 completo, `release.yml` con
  `mvn -Ppublish deploy` y secrets) — la publicacion a Maven Central sigue siendo manual,
  según lo deliberado en el plan `docs-for-agent-ia/maven-central-publishing/maven-central-publishing-opencode.md`.
- No se configuran secrets, tokens ni credenciales en el repositorio.
- No se modifican los POMs, el código, ni las coberturas/checks de JaCoCo del reactor.