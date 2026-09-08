# Maven Central publishing — configuration for release `1.0.0` (stages 1–5, 7, part of 9)

| | |
|---|---|
| **Date** | 2026-09-08 |
| **Type** | Feature |
| **Module(s)** | `rate-limit` (parent + `core`/`inmemory`/`redis`), `examples`, `README.md`, `CHANGELOG.md`, `PUBLISHING.md` |
| **Status** | Not merged — branch `feature/maven-central-publishing`. Implements the pipeline; the actual publication (stages 6, 10) is external (Central Portal account/credentials) and out of branch scope. |
| **Branch** | `feature/maven-central-publishing` |
| **Ticket / issue** | `N/A` |
| **Author(s)** | `N/A` (opencode-assisted session) |

## Summary

Prepares the repository so that `1.0.0` can be published to Maven Central with a single,
reproducible command (`mvn -Ppublish clean deploy`): complete POM metadata for the three
publishable modules, version `1.0.0`, sources/Javadoc jars, GPG signing and the Sonatype
`central-publishing-maven-plugin` wired behind an opt-in `publish` profile. Validated by an
external consumer project that resolves `rate-limit-core`/`-inmemory`/`-redis:1.0.0` and runs.

## Motivation

Following the execution plan `docs-for-agent-ia/maven-central-publishing/maven-central-publishing-opencode.md`,
the goal is a stable, reproducible release process — not a hand-assembled JAR upload. The plan
explicitly defers CI/CD (stage 8) until after the first manually validated `1.0.0`, so this
change only adds the build-side capabilities and documentation that the publication needs.

## What changed

- **Version**: reactor bumped `0.0.1-SNAPSHOT` → `1.0.0` (parent and the three modules);
  `examples/pom.xml` dependencyManagement mirrored to `1.0.0` (examples' own version stays
  `0.0.1-SNAPSHOT` — not published).
- **POM metadata** (`rate-limit/pom.xml`): added `<licenses>` (MIT), `<developers>`
  (`Lauto5` + GitHub noreply contact), `<scm>` (git + GitHub url); normalized `url`/`scm` to
  `Lauto5` casing to match the git remote. Modules inherit the metadata from the published
  parent (declared as a reactor POM, per plan stage 2).
- **Module descriptions** translated to English.
- **Sources + Javadoc jars** (stage 3): `maven-source-plugin` (`jar-no-fork`) and
  `maven-javadoc-plugin` (`jar`) bound in the three publishable modules; plugin versions in
  parent `pluginManagement`. Fixed one broken `{@link}` Javadoc reference in
  `RateLimitAtomicOperation`. `mvn clean verify` now produces `<module>-sources.jar` and
  `<module>-javadoc.jar` per module with no Javadoc errors (only pre-existing "missing
  comment" warnings).
- **`publish` profile** (stages 4–5, `rate-limit/pom.xml`): adds `maven-gpg-plugin` (sign)
  and `central-publishing-maven-plugin` (`extensions=true`) with `autoPublish=false`,
  `waitUntil=validated`, overridable via `-Dcentral.autoPublish` / `-Dcentral.skipPublishing`.
  Release = `mvn -Ppublish clean deploy`; default `clean verify`/`install` stay keyless.
  No secrets in the POMs (credentials live in `~/.m2/settings.xml`, server id `central`).
- **Docs**: `README.md` installation placeholders → `1.0.0`; new `CHANGELOG.md` (`1.0.0`
  release notes: algorithms, modules, compatibility, example, API stability) and
  `PUBLISHING.md` (release runbook: prerequisites, one-command release, dry run, external
  consumer validation, checklist).

## What did NOT change

- **Architecture, public API and algorithms**: untouched (plan principle). Only a Javadoc
  link text fixed; no production behavior change.
- **CI/CD** (stage 8) is deliberately not added yet — the plan defers it until after the
  first validated `1.0.0` publication.
- `examples/` own `0.0.1-SNAPSHOT` version and module names; `debug-scripts/`; `.gitignore`.

## Breaking changes

- [x] No, it is backward compatible.
- [ ] Yes — see details and migration guide below.

`N/A` — no API or behavior change; version moves from a SNAPSHOT (never published) to `1.0.0`.

## Public API impact

No changes to the public API.

## Testing

- [x] `mvn -f rate-limit/pom.xml clean verify` → BUILD SUCCESS (**234 tests**, unit +
  Testcontainers integration + contract + concurrency), now including sources/Javadoc jar
  generation.
- [x] `mvn -f rate-limit/pom.xml install -DskipTests` → artifacts `1.0.0` installed locally.
- [x] External consumer validation (stage 7, `/tmp/opencode/consumer-test`, outside the
  repo): resolves `io.github.lauto5:rate-limit-core|inmemory|redis:1.0.0` from the local
  repository; in-memory consumer **runs** a Fixed Window policy and rate-limits correctly
  (`allowed=true,true,false` for a 2/min limit); redis consumer builds a `RedisStore`.
- [x] Manual verification — NOTE: the `publish` profile (GPG sign + Central upload) could not
  be exercised end-to-end locally: no GPG secret key and no Central Portal credentials on this
  machine (external prerequisites). Config is passive by default.

## Known risks and considerations

- Stage 4/5 sign/upload remain unverified until the owner provides a GPG key and Central
  Portal user token (server `central` in `settings.xml`) and runs `mvn -Ppublish clean deploy`.
- `consumer-redis` only proves resolution/construction; a live rate-limit operation against
  Redis was not exercised (no Redis server needed for the build; integration coverage already
  exists in the reactor via Testcontainers).
- The `url`/`scm` were normalized to `Lauto5` (matches `git@github.com:Lauto5/Rate-Limit.git`
  and the MIT `LICENSE`); GitHub URL casing is case-insensitive.
- Javadoc still emits many "missing public comment" **warnings** (not errors); a future
  doc-comments pass for the whole API is a candidate improvement, not part of this change.

## Related work left out of this change

- Stage 6 (summon): Central Portal namespace `io.github.lauto5` verification + credentials.
- Stage 8 (deferred by the plan): `.github/workflows/release.yml` — recommended item once `1.0.0` is published and validated.
- Stage 9 (git): tag `v1.0.0` at publication time.
- Stage 10 (consumption check from Central).
- `docs-for-agent-ia/` remains gitignored (decision `DC-01`/D3 from the previous feature still pending).

## References

- Related documentation: `docs-for-agent-ia/maven-central-publishing/maven-central-publishing-opencode.md` (plan; local-only), `PUBLISHING.md` (runbook), `CHANGELOG.md`
- Sonatype docs: [Publishing by using the Maven plugin](https://central.sonatype.org/publish/publish-portal-maven)
- PR: `<link>` (`N/A` — feature not merged yet)