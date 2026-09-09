# Releasing Rate-Limit to Maven Central

**Status:** `1.0.0` is published and live on Maven Central (`io.github.lauto5:*`),
released on 2026-09-08 with `-Dcentral.autoPublish=true`.

This document describes the official release pipeline of the library (feature
`maven-central-publishing`). Releasing is performed in two phases, by design:

1. **Pipeline (Stage 5)**: `mvn -Ppublish clean deploy` builds the full set of artifacts
   (compile, test, sources, javadoc, GPG signatures, checksums) as a single Central bundle
   and uploads it to the [Central Portal](https://central.sonatype.com) for validation.
   The final publish of a validated deployment is done from the portal UI.
2. **Automation (Stage 8)**: CI/CD for future releases is intentionally deferred until the
   first `1.0.0` release has been validated manually. See `docs-for-agent-ia/maven-central-publishing/maven-central-publishing-opencode.md`.

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
mvn -f rate-limit/pom.xml -Ppublish clean deploy \
    -Dgpg.keyname=<KEY_ID> \
    -Dgpg.passphrase=<PASSPHRASE>
```

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

## Local dry run (bundle only, no upload)

```bash
mvn -f rate-limit/pom.xml -Ppublish clean deploy -Dcentral.skipPublishing=true
```

Generates the bundle under `target/central-publishing/central-bundle.zip` without uploading
(it still requires the GPG key to sign).

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

- [ ] `mvn -f rate-limit/pom.xml clean verify` — green (unit, integration, concurrency).
- [ ] `mvn -f examples/pom.xml clean verify` — green against the installed `1.0.0` artifacts.
- [ ] External consumer resolves and runs against the local `1.0.0` artifacts.
- [ ] Bundle uploaded and validated via `mvn -Ppublish clean deploy`.
- [ ] Publish the validated deployment in the Central Portal.
- [ ] Tag `v1.0.0` (Stage 9) and update `CHANGELOG.md` / release notes.
- [ ] After the first manual release: enable Stage 8 (`.github/workflows/release.yml`).

## Source of truth

The canonical execution plan for this feature is
`docs-for-agent-ia/maven-central-publishing/maven-central-publishing-opencode.md`
(gitignored, local only). It defines the DoD, the security rules and what is explicitly out
of scope (Spring Boot starter, Gradle publishing, new algorithms, Micrometer, etc.).