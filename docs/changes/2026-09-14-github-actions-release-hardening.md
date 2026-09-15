# Endurecimiento de CI/CD: dry-run sin producción, filtro de tags y guard SemVer

| | |
|---|---|
| **Date** | 2026-09-14 |
| **Type** | Fix |
| **Module(s)** | CI (`.github/workflows/release.yml`, `.github/workflows/release-dry-run.yml`), GitHub secrets, `PUBLISHING.md` |
| **Status** | Merged (commit en `main`) |
| **Branch** | `main` |
| **Ticket / issue** | `N/A` (revisión `docs-for-agent-ia/github-actions-diagnostico-opencode.md`, gitignored) |
| **Author(s)** | `N/A` (opencode-assisted session) |

## Summary

Aplicación de un diagnóstico de mejora sobre el pipeline de release tag-driven: se separó el
dry-run del workflow de publicación en un archivo propio **sin acceso al environment
`production`**, se simplificó el filtro de tags (`v*.*.*`, glob de GitHub, no regex), se
reforzó el guard de versión con validación SemVer estricta (rechazo de SNAPSHOT y de tags
malformados) y se migraron los secrets GPG del environment al scope de repo para que el
dry-run pueda firmar sin tocar credenciales de Central.

## Motivation

El diagnóstico identificó dos problemas de seguridad/robustez antes del primer release real
tag-driven:

1. **El filtra de tags usaba un patrón con pretensión de regex** (`v[0-9]+.[0-9]+.[0-9]+`).
   GitHub Actions interpreta el filtro de tags como glob, no como regex; el comportamiento
   no está garantizado. Con el glob amplio `v*.*.*` la validación fina la hace el workflow.
2. **El dry-run pasaba por el mismo job que el release real**, con `environment: production`
   a la vista: un dry-run tenía disponibles los secrets de publicación aunque no los usara
   (violación de least privilege). Un bug en el camino del dry-run podría llegar a deploy.
3. Menor: la validación `tag ↔ pom.xml` era correcta pero no rechazaba explícitamente
   versiones `-SNAPSHOT` ni tags como `v1.2`, `vfoo`.

## What changed

### `.github/workflows/release.yml`

- Trigger: `on.push.tags` pasa a `- 'v*.*.*'` (glob de GitHub). Se elimina `workflow_dispatch`.
- Nuevo header documentando la separación de secrets (GPG a nivel repo; Maven en el
  environment `production`).
- Guard de versión reforzado (bash, se ejecuta solo en `ref_type == 'tag'`):
  - rechazo si el tag no es exactamente `vMAJOR.MINOR.PATCH` (`^v[0-9]+\.[0-9]+\.[0-9]+$`);
  - rechazo si el `<version>` del reactor contiene `SNAPSHOT`;
  - comparación estricta tag(v-sin prefijo) ↔ `<version>`.
- El paso `Deploy to Maven Central` deja de bifurcar por `inputs.dry_run`: solo publica
  (`autoPublish=true`, `waitUntil=published`).

### `.github/workflows/release-dry-run.yml` (nuevo)

- `workflow_dispatch`; **sin** `environment` → no tiene `MAVEN_USERNAME`/`MAVEN_PASSWORD`.
- `permissions: contents: read`; `concurrency` propio con `cancel-in-progress: true`.
- Mismo paso de import/prime de clave GPG (aislamiento `GNUPGHOME`, agente primado).
- Deploy en modo seco: `-Dcentral.skipPublishing=true`; **no** se genera `settings.xml` con
  credenciales (el plugin 0.11.0 saltea bundle + upload).

### Secrets (GitHub)

- `GPG_SIGNING_KEY` y `GPG_PASSPHRASE` migrados de `production` a **repo-level** (necesarios
  para firmar en CI, dry-run y release; GitHub no los expone a jobs de PRs de forks).
- `MAVEN_USERNAME` y `MAVEN_PASSWORD` quedan únicamente en el environment `production`
  (release real tag-driven).

### Docs

- `PUBLISHING.md`: split de secrets (repo vs `production`), sección dry-run apuntando a
  `release-dry-run.yml`, aclaración de fecha (2026-09-14 local / 15 UTC), checklist marcado.
- Registro previo renombrado a `2026-09-14-...` y fecha corregida (convención de fecha
  local del repo).

## Impact / behavior

- El release tag-driven ya no es alcanzable por dispatch; el dry-run ya no ve credenciales
  de Central. Separación limpia por least privilege.
- El guard acepta únicamente `vMAJOR.MINOR.PATCH` no-SNAPSHOT y consistente con el pom.
- Los secrets GPG siguen siendo los de la clave `AE4005A75393C9D7` (rotados el
  2026-09-14); solo cambió su scope.

## Rollback / risk

- Actualizar el secret nuevo con el mismo comando: `gh secret set GPG_SIGNING_KEY --env
  production` (en caso de revertir al diseño de un solo workflow).
- No validado end-to-end con un tag real (evitado a propósito: el próximo tag real debe ir
  con una versión nueva bumpada, o se intentaría republicar `1.0.0`). El dry-run del
  workflow separado es la validación equivalente.
- `setup-java@v4` sigue emitiendo aviso de deprecación (seguimiento pendiente, no mover el
  happy path).