# Rotación de la clave GPG de firma y corrección del firmado en el workflow de release

| | |
|---|---|
| **Date** | 2026-09-15 |
| **Type** | Fix |
| **Module(s)** | CI (`.github/workflows/release.yml`), GitHub Environment `production`, `PUBLISHING.md` |
| **Status** | Merged (commit en `main`) |
| **Branch** | `main` |
| **Ticket / issue** | `N/A` (opencode-assisted session) |
| **Author(s)** | `N/A` |

## Summary

Regeneración de la clave GPG de firma desde cero (`AE4005A75393C9D7`, RSA-4096, reemplaza a
`40139A5C07B4EBA4`) y corrección del paso de firmado de `.github/workflows/release.yml` para
que el dry-run del workflow quede verde. El bloqueante era un `GPG_PASSPHRASE` con un valor de
6605 caracteres (debería ser la passphrase corta real de la clave), que rompía gpg con
`gpg: signing failed: Too much data for IPC layer`.

## Motivation

Desde el primer dry-run del Stage 8, el paso "Deploy to Maven Central" fallaba al firmar.
Dos causas encadenadas:

1. **Mecanismo de entrega de la passphrase**: `-Dgpg.passphrase` y la variable
   `MAVEN_GPG_PASSPHRASE` son canales depreciados del `maven-gpg-plugin` (3.2.8) que pasan la
   passphrase por el IPC de loopback y fallaban en los runners hospedados. Se probó además
   que incluso `gpg` plano con `--pinentry-mode loopback --passphrase` reproducía el error, lo
   que descartó al plugin como única causa.
2. **Valor inválido del secret**: un workflow de diagnóstico (temporal) midió
   `PASS_LEN=6605` en el secret `GPG_PASSPHRASE`, claramente por encima del buffer IPC de
   loopback de gpg (`plain-fd` con una passphrase corta firmaba OK). El valor se había setteado
   mal durante la configuración inicial.

Durante el diagnóstico se confirmó además que el *gpg-agent ambiente* del runner (registrado en
`~/.gnupg`) se comporta mal con gpg 2.4: incluso un prime con loopback en el home por defecto
falla con el mismo error de IPC. Por eso la solución usa un `GNUPGHOME` aislado.

## What changed

### Clave GPG renovada (decisión del usuario)

- Nueva clave RSA-4096, mismo uid, **sin
  expiración** (continuidad del pipeline de CI).
  - Key id (largo): `AE4005A75393C9D7`
  - Fingerprint: `D8A3 90DA 7E6C 673D 8660 C43E AE40 05A7 5393 C9D7`
- Clave pública distribuida y verificada en los 3 keyservers que Central soporta:
  `keyserver.ubuntu.com` (OK, retrievable desde ring aislado), `keys.openpgp.org` (OK),
  `pgp.mit.edu` (envío OK, aunque el servidor suele no responder ante consultas).
- Secrets del environment `production` actualizados vía `gh secret set` (sin imprimir el
  valor): `GPG_SIGNING_KEY` = armor del secreto nuevo; `GPG_PASSPHRASE` = passphrase real
  (largo 13).

### Workflow de release endurecido (`.github/workflows/release.yml`)

- `setup-java` dejó de recibir `gpg-private-key`/`gpg-passphrase` (inputs que inyectan el canal
  depreciado en `settings.xml`).
- Nuevo paso **"Install & prime GPG signing key"**:
  1. Importa el armor desde `secrets.GPG_SIGNING_KEY` en un `GNUPGHOME` aislado
     (`$RUNNER_TEMP/gpg-home`), **antes** de escribir `gpg.conf`/`gpg-agent.conf` (importar con
     `pinentry-mode loopback` activo rompe el self-check de gpg 2.4 en modo batch).
  2. Configura `pinentry-mode loopback` + `allow-loopback-pinentry` + cache del agente
     (`default-cache-ttl 3600`, `max-cache-ttl 7200`).
  3. **Prima** el agente firmando un archivo fantasma con la passphrase; si la passphrase es
     incorrecta, falla el paso (loud) en lugar de estallar en Maven.
  4. Expone `keyid` y `GNUPGHOME` al resto de los pasos (`GITHUB_OUTPUT`/`GITHUB_ENV`).
- El paso de deploy firma **sin passphrase**: el agente la sirve desde su caché. Se eliminó
  `-Dgpg.passphrase` y `MAVEN_GPG_PASSPHRASE` del workflow.
- El keyid se resuelve dinámicamente en CI (solo hay una clave en el keyring del runner), no
  hay referencias hardcodeadas a fingerprints en el repo.

## Impact / behavior

- El dry-run del workflow (`workflow_dispatch` con `dry_run=true`, run `34916234613`) quedó
  **verde**: imports OK, prime OK, firma `1 + 4 + 4 + 4` archivos con `AE4005A75393C9D7` y
  `BUILD SUCCESS`. Sin upload a Central.
- Localmente la misma pipeline (`-Ppublish clean deploy -Dcentral.skipPublishing=true`) se
  validó con la clave nueva (4/4 módulos SUCCESS).
- Los artefactos de `1.0.0` ya publicados conservan su firma con la clave vieja (pública aún
  en los keyservers); la clave nueva se usa para releases futuras.

## Rollback / risk

- Si una release futura falla por firma, verificar que la pública de `AE4005A75393C9D7` siga
  en los 3 keyservers (rotación completa requiere re-subirla).
- `setup-java@v4` emite aviso de deprecación (migrar a `v5`/`v6` es seguimiento, no se tocó
  para no mover el happy path ya validado).
- El primer release real (`tag vX.Y.Z`) requiere bump de versión en `rate-limit/pom.xml` y
  `examples/pom.xml` + `CHANGELOG.md` antes de tagear; el dry-run verde es el DoD del Stage 8.