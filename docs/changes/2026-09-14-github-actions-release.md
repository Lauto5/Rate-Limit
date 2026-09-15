# Release tag-driven a Maven Central vía GitHub Actions (Stage 8)

| | |
|---|---|
| **Date** | 2026-09-14 |
| **Type** | Feature |
| **Module(s)** | CI (`.github/workflows/release.yml`), `rate-limit/pom.xml`, `PUBLISHING.md` |
| **Status** | Not merged — branch `feature/github-actions`. |
| **Branch** | `feature/github-actions` |
| **Ticket / issue** | `N/A` |
| **Author(s)** | `N/A` (opencode-assisted session) |

## Summary

Completa el Stage 8 del plan de publicación con un workflow `release.yml` tag-driven: un tag
`vX.Y.Z` dispara `mvn -Ppublish clean deploy` con una comprobación de seguridad de versiones
(el tag debe coincidir con el `<version>` del reactor) y publicación automática en Maven
Central (esperando hasta `PUBLISHED`).Corre en el environment `production` leyendo las
credenciales y la clave GPG desde GitHub Secrets.

## Motivation

La publicación de `1.0.0` se hizo manualmente (decisión deliberada del plan). Para release
futuras hace falta repetibilidad y seguridad: que el artefacto publicado corresponda a la
versión tageada y que nadie pueda publicar un `SNAPSHOT` o una versión desacoplada del tag.
El workflow con guard de versión y secrets en el environment `production` cubre ambos
requisitos del Stage 8 (workflow definido + secrets configurados como GitHub Secrets).

## What changed

- **`.github/workflows/release.yml`**:
  - triggers: `push` de tags `vX.Y.Z` (`v[0-9]+.[0-9]+.[0-9]+`) y `workflow_dispatch` con
    input booleano `dry_run` para validar el pipeline sin subir nada a Central.
  - corre en el environment `production` (secrets de entorno).
  - JDK 17 Temurin con `cache: maven`; importa la clave GPG privada vía `setup-java`
    (`gpg-private-key` + `gpg-passphrase`).
  - genera `~/.m2/settings.xml` con el server `central` usando `MAVEN_USERNAME` /
    `MAVEN_PASSWORD` del environment (el token del Central Portal, nunca en el repo).
  - resuelve el key id de la clave firmante recién importada (`--list-secret-keys`,
    `awk`/colons) y firma con `-Dgpg.keyname`.
  - **guard de versión**: en variantes de tag compara el `<version>` de
    `rate-limit/pom.xml` con `github.ref_name` (sin la `v`); si no coinciden, `::error` y
    exit 1 sin publicar nada.
  - deploy: `-Dcentral.autoPublish=true -Dcentral.waitUntil=published` (fail si Central
    no confirma la publicación). `permissions: contents: read` y `concurrency` sin cancel.
- **`rate-limit/pom.xml`**: el `<waitUntil>` del `central-publishing-maven-plugin` pasa de
  valor literal `validated` a `${central.waitUntil}` con default `validated` en properties,
  para que el workflow pueda esperar hasta `published` vía `-D` sin cambiar el
  comportamiento por defecto.
- **`PUBLISHING.md`**: sección nueva "Automated release — tag-driven (via GitHub Actions)"
  con la tabla de secrets requeridos, el guard y el dry-run; dry-run local corregido (en
  `0.11.0` `skipPublishing` no genera bundle); checklist dividido en flujo manual (1.0.0)
  y flujo automatizado.

## What did NOT change

- No se publica nada en esta rama: el trigger tag-driven solo se activa con un tag real
  `vX.Y.Z` en el futuro; nada de esto ejecuta en CI ni en PR.
- No se añaden secrets reales al repositorio (los valores viven en el environment
  `production` de GitHub, configurados por el usuario).
- No cambia la API pública, los módulos, ni la lógica del reactor.
- No se modifica `central.autoPublish`/`skipPublishing` por defecto (siguen `false`).