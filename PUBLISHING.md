# Releasing Rate-Limit to Maven Central

**Status:** `1.0.0` is published and live on Maven Central (`io.github.lauto5:*`),
released on 2026-09-08 with `-Dcentral.autoPublish=true`. The `rate-limit-spring-boot` module is
part of the reactor and will be included in the next release.

This document describes the official release pipeline of the library (feature
`maven-central-publishing`). Releasing is performed in two phases, by design:

1. **Pipeline (Stage 5)**: `mvn -Ppublish clean deploy` builds the full set of artifacts
   (compile, test, sources, javadoc, GPG signatures, checksums) as a single Central bundle
   and uploads it to the [Central Portal](https://central.sonatype.com) for validation.
   The final publish of a validated deployment is done from the portal UI.
2. **Automation (Stage 8)**: `.github/workflows/release.yml` publishes future releases
   automatically. A tag `vX.Y.Z` (e.g. `v1.0.1`, `v2.0.0`) triggers the workflow, which
   verifies the tag matches the reactor `<version>` and runs the deploy with `autoPublish`.
   The `1.0.0` release was published manually first; the workflow is validated with a
   dry-run trigger before each tag.

## Prerequisites

- **GPG key** registered and passphraseless-capable in your environment:
  `gpg --full-generate-key`. Export a public key id with
  `gpg --list-secret-keys --keyid-format=long` and pass it with `-Dgpg.keyname=...`.
- **Central Portal namespace** `io.github.lauto5` verified (Stage 6, done once).
- **Credentials** outside the repository, in `~/.m2/settings.xml`:

```xml
<settings>
  <servers>
    <server>
      <id>central</id>
      <username><!-- your central.sonatype.com user token username --></username>
      <password><!-- your central.sonatype.com user token password --></password>
    </server>
  </servers>
</settings>
```

The token is generated in the Central Portal (see Sonatype docs, *generating a portal token*).
Never commit tokens or signing secrets to the repository.

## One-command release

```bash
export MAVEN_GPG_PASSPHRASE=<PASSPHRASE>   # used by the maven-gpg-plugin (passphraseEnvName)
mvn -f rate-limit/pom.xml -Ppublish clean deploy \
    -Dgpg.keyname=<KEY_ID>
```

Do **not** pass `-Dgpg.passphrase=...`: that channel is deprecated and breaks gpg's loopback IPC
on hosted runners with `Too much data for IPC layer`. Locally you can also rely on your
`gpg-agent` cache (prime it once with `gpg --sign ...`) and skip the env var.

What `-Ppublish` adds on top of a normal build:

- `maven-gpg-plugin`: signs every artifact (jar, pom, sources, javadoc).
- `central-publishing-maven-plugin` (`extensions=true`): intercepts `deploy`, bundles the
  signed artifacts and checksums into `central-bundle.zip`, uploads it to the Central Portal
  and waits for validation.

`autoPublish` is `false` by default: after validation you confirm the publish in the portal
(Deployments page). For unattended/CI publishes enable it explicitly without changing the
defaults:

```bash
mvn -f rate-limit/pom.xml -Ppublish clean deploy -Dcentral.autoPublish=true
```

## Local dry run (no upload)

```bash
mvn -f rate-limit/pom.xml -Ppublish clean deploy -Dcentral.skipPublishing=true
```

Builds and signs the full artifact set (jar, sources, javadoc, POM) without uploading
anything. Note that in `central-publishing-maven-plugin` `0.11.0` `skipPublishing` skips the
bundle generation and the upload entirely; to also validate the bundle content use the
one-command release against the portal with `autoPublish=false` and discard the deployment.

## Automated release — tag-driven (via GitHub Actions)

Two workflows, separated by least privilege:

- **`.github/workflows/release.yml`** — tag-driven, real publishes. Tagging **vX.Y.Z**
  (`v1.0.1`, `v1.1.0`, `v2.0.0`, ...) runs:

  ```text
  git tag vX.Y.Z  ->  GitHub Actions (environment: production)  ->  mvn -Ppublish clean deploy
                  ->  Maven Central (autoPublish, waits until PUBLISHED)
  ```

- **`.github/workflows/release-dry-run.yml`** — `workflow_dispatch`, builds + signs +
  verifies the full pipeline **without** the `production` environment (no Central token
  available, `-Dcentral.skipPublishing=true`, nothing leaves the runner).

The release workflow guards version safety: the tag must be exactly `vMAJOR.MINOR.PATCH`
(SemVer-clean, no SNAPSHOT) and match the reactor `<version>` in `rate-limit/pom.xml`;
otherwise it fails with `::error` and publishes nothing. Bump the version in
`rate-limit/pom.xml` (and `examples/pom.xml`) before tagging.

### Secrets

Split by scope so a dry-run never sees production credentials:

**Repo-level** (needed by CI, dry-run and release for signing):

| Secret | Purpose |
|---|---|
| `GPG_SIGNING_KEY` | ASCII-armored private key; must be the key already distributed to a Central-supported keyserver |
| `GPG_PASSPHRASE` | Passphrase of the signing key |

**GitHub Environment `production`** (release only):

| Secret | Purpose |
|---|---|
| `MAVEN_USERNAME` | Central Portal user token username (`settings.xml` server `central`) |
| `MAVEN_PASSWORD` | Central Portal user token password |

The exported private key must be the same key whose public key is reachable on the PGP
keyservers Sonatype checks (`keyserver.ubuntu.com`, `keys.openpgp.org`, `pgp.mit.edu`),
otherwise Central rejects the signatures at validation time.

`GPG_PASSPHRASE` must be the real (short) passphrase of the signing key. A large value
(e.g. the armored key pasted by mistake) breaks gpg's loopback IPC with
`gpg: signing failed: Too much data for IPC layer`.

### GPG signing inside the workflow

The workflow imports the private key into an **isolated** `GNUPGHOME` (`$RUNNER_TEMP/gpg-home`)
and **primes the gpg-agent**: a scratch file is signed once with the passphrase so the agent
keeps it cached, and Maven then signs without any passphrase flag (the `maven-gpg-plugin`
passphrase channels are deprecated and fail on hosted runners). The key id is resolved
dynamically; nothing is hardcoded.

**Current signing key:** `AE4005A75393C9D7` (RSA-4096, uid `lautaro nahuel ponce
<lauto5dev@gmail.com>`, distributed on the 3 keyservers). On key rotation: regenerate,
re-upload the public key to the 3 keyservers, and update the repo-level
`GPG_SIGNING_KEY` / `GPG_PASSPHRASE` secrets.

### Dry-run

`gh workflow run release-dry-run.yml` builds, signs and verifies everything exactly like a
release but skips the upload (no bundle, nothing reaches Central) and cannot access the
Central Portal credentials (no `production` environment).

## Releasing a new version — step by step

1. **Bump the version** in `rate-limit/pom.xml` (the reactor `<version>`) and keep
   `examples/pom.xml` `dependencyManagement` versions in sync with it. Following the version
   policy in the contributor docs.
2. **Update `CHANGELOG.md`** with the new version entry (content moved out of `Unreleased`).
3. **Validate locally**: `mvn -B -f rate-limit/pom.xml clean verify` and
   `mvn -B -f examples/pom.xml clean verify` green. This is what CI runs on the PR.
4. **Open and merge the PR** to `main` (CI + 1 review required by branch protection).
5. **Run a dry-run release**: `gh workflow run release-dry-run.yml` → green (builds, tests,
   signs all artifacts, uploads nothing, no production secrets).
6. **Tag the release** on `main`: `git tag vX.Y.Z && git push origin vX.Y.Z`. The
   `release.yml` workflow validates that the tag matches the reactor version (SemVer-clean,
   no SNAPSHOT), otherwise it fails with `::error` and publishes nothing.
7. **Wait for the workflow** to reach `PUBLISHED` (it deploys with `autoPublish` and
   `waitUntil=published`). If it fails, see below.

## If a release fails

- **Version validation failed (tag mismatch / SNAPSHOT)**: nothing was published. Fix the tag
  or the version and re-tag. Delete the bad tag with `git tag -d vX.Y.Z` locally and
  `git push origin :refs/tags/vX.Y.Z`.
- **GPG/signing failed**: check the "Install & prime GPG signing key" step. Common causes:
  expired/mismatched key, wrong `GPG_PASSPHRASE`, public key not reachable on the keyservers
  Sonatype checks. The workflow fails before uploading anything.
- **Central rejected the bundle at validation**: the deploy step exits non-zero (the build
  waits until `published`). Check the Central Portal Deployments page for the rejection reason
  (bad POM, missing sources/javadoc JAR, signature problem). Fix, then re-tag a corrected
  version — Central does not allow re-publishing the same version once rejected in a way that
  requires a new one.
- **Manual/local release**: run the portal flow with `autoPublish=false` and either link the
  deployment from the portal UI or discard it.

## Validating artifacts as an external consumer

After `mvn -f rate-limit/pom.xml install` (or a deploy), verify the artifacts from a project
**outside** this repository (so no `relativePath`/GitHub resolution is used):

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

For `rate-limit-redis`, also resolve and compile against `io.github.lauto5:rate-limit-redis:1.0.0`
(executing rate-limit operations requires a live Redis).

## Release checklist

Manual flow (used for `1.0.0`):
- [x] `mvn -f rate-limit/pom.xml clean verify` — green (unit, integration, concurrency).
- [x] `mvn -f examples/pom.xml clean verify` — green against the installed artifacts.
- [x] External consumer resolves and runs against the local artifacts.
- [x] Bundle uploaded and validated via `mvn -Ppublish clean deploy`.
- [x] `1.0.0` published on Central (2026-09-08, `autoPublish`).

Automated flow (Stage 8, `.github/workflows/release.yml`):
- [x] Export the signing private key (`gpg --armor --export-secret-keys`) and store it in
      the `production` GitHub Environment as `GPG_SIGNING_KEY` + `GPG_PASSPHRASE`, plus
      `MAVEN_USERNAME` / `MAVEN_PASSWORD` for the Central Portal token.
- [x] Verify the distributed public key is still reachable on the supported PGP keyservers.
- [x] Run the workflow `workflow_dispatch` `release-dry-run.yml` before first tag
      (green, 2026-09-14 local / 2026-09-15 UTC, key `AE4005A75393C9D7`).
- [ ] Bump `rate-limit/pom.xml` reactor version (and keep `examples/pom.xml` in sync) +
      `CHANGELOG.md`. Includes the `rate-limit-spring-boot` module in the bundle.
- [ ] Tag `vX.Y.Z` — the workflow validates the version match and publishes to Central
      (all four modules: core, inmemory, redis, spring-boot).

## Source of truth

The canonical execution plan for this feature is
`docs-for-agent-ia/maven-central-publishing/maven-central-publishing-opencode.md`
(gitignored, local only). It defines the DoD, the security rules and what is explicitly out
of scope (Spring Boot starter, Gradle publishing, new algorithms, Micrometer, etc.).