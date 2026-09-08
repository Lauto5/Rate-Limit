<!--
No delete sections: if not applicable, leave "N/A" with a brief reason.
-->

# Project cleanup and governance — Stages 3-4 (reuse & simplification, project organization)

| | |
|---|---|
| **Date** | 2026-09-08 |
| **Type** | Chore (reuse, package organization; no functional change intended) |
| **Module(s)** | `rate-limit-core`, `rate-limit-inmemory`, `rate-limit-redis`, `examples` |
| **Status** | Not merged — branch `feature/project-cleanup-and-governance`; stages 5-8 still pending. Companion to `2026-09-08-project-cleanup-and-governance-stages-0-2.md`. |
| **Branch** | `feature/project-cleanup-and-governance` |
| **Ticket / issue** | `N/A` |
| **Author(s)** | `N/A` (opencode-assisted session) |

## Summary

Applies the next two execution stages of the repository governance feature on top of `ec17186`: **Stage 3 — Reuse & Simplification** removes a three-way duplication in the algorithm state codecs, adds the missing identifier validation in the in-memory store, and centralizes the duplicated Maven configuration of the examples; **Stage 4 — Project Organization** removes the only `domain → application` dependency by moving `StateCodec` into `domain`, relocates the concrete loggers into the `application` layer, normalizes residual formatting in the `api` factory, and documents the decisions `D1`, `OR-02` and `OR-05`.

Commits: `d14aed4` (RS-01), `838c754` (RS-02), `d549d14` (RS-03), `1200bcb` (OR-01/D1), `a3c5d7c` (OR-03), `335eb72` (OR-02). `OR-05` was resolved by the repository owner in `f82727a`.

## Motivation

Stage 3 addresses three SHOULD-FIX findings from the audit: the pipe-delimited CODEC was written three times (one per algorithm), `InMemoryStore` accepted `null`/empty identifiers while `RedisStore` rejected them, and the two example POMs duplicated version/encoding configuration that had already drifted from the reactor parent. Stage 4 addresses the only "large" architectural inconsistency found in the audit: `domain.algorithm` imported `application.ports.out.StateCodec`, violating the pure-domain rule `03 §6`; additionally the concrete loggers lived in an orphan top-level package `logging/` instead of in the layer that owns their port.

## What changed

**Stage 3 — Reuse & Simplification.**

- `RS-01` (`d14aed4`) — extracted a package-private `PipeDelimitedCodec` in `io.github.lauto5.rateLimit.domain.algorithm` with `encode(String, String)` / `decode(byte[])`; `FixedWindow`, `LeakyBucket` and `TokenBucket` algorithm codecs now reuse it while keeping their own typed parsing. Removed the now-unneeded `StandardCharsets` imports. New unit test `PipeDelimitedCodecTest` (round-trip, delimiter, UTF-8).
- `RS-02` (`838c754`) — `InMemoryStore.executeAtomically` now throws `IllegalArgumentException("Identifier must not be null or empty")` for `null`/empty identifiers, mirroring `RedisStore`. Two new tests in `InMemoryStoreUnitTest` (`ValidationCases`).
- `RS-03` (`d549d14`) — created `examples/pom.xml` (packaging `pom`) that centralizes encoding, Java 8 target, `exec-maven-plugin` (3.5.0) and `slf4j-simple` (2.0.18) versions (kept in sync with `rate-limit/pom.xml`), plus `dependencyManagement` for the three rate-limit modules; both example POMs dropped their duplicated `properties` and hardcoded versions. Added a "Configuración" section to `examples/README.md` (kept in Spanish, matching the file).

**Stage 4 — Project Organization.**

- `OR-01` / decision `D1` (`1200bcb`) — moved `StateCodec` from `io.github.lauto5.rateLimit.application.ports.out` to `io.github.lauto5.rateLimit.domain.algorithm` (`git rename`, 72% similarity). 21 files updated in-repo (algorithm implementations, `RateLimitAlgorithm`, `AtomicOperation`, `RateLimitAtomicOperation`, `VersionedStateCodec`, stores, test doubles and unit tests). `examples/` do not reference `StateCodec` and were unaffected. This removes the last `domain → application` dependency.
- `OR-03` (`a3c5d7c`) — moved `ConsoleLogger` and `NoOpLogger` from `io.github.lauto5.rateLimit.logging` to `io.github.lauto5.rateLimit.application.logging` (both `git rename`-detected). Updated imports in `rate-limit-*` (15 files) and `examples/` (2 files), plus `README.md`.
- `OR-02` (`335eb72`) — documented that `api/Algorithm` instantiating the implementations is an acceptable factory (it returns interface types, so no impl types leak into the public API) and normalized the residual brace style and trailing whitespace in that file (leftover of `CS-04`).
- `OR-05` (resolved by the repository owner in `f82727a`) — `debug scripts/` renamed to `debug-scripts/` (git-detected rename), matching the no-spaces convention; documented in the audit (`07`).

## What did NOT change

- **Public API signatures**: no method was added, removed or renamed; `RateLimitResult` contract unchanged.
- **Algorithm logic, wire format and storage**: codec byte output is byte-for-byte identical (same `first|second` UTF-8 payload, same split/parse semantics, including malformed-payload behavior); `InMemoryStore` behavior is unchanged for valid identifiers.
- **Module structure and dependency direction**: `StateCodec` moved within `rate-limit-core` (same module); `examples/` still consume the installed snapshots and are not part of the reactor.
- **Package base name**: `io.github.lauto5.rateLimit` was intentionally left untouched (decision `D2`, major-bump rename tracked separately).
- **The governed documentation** (`docs-for-agent-ia/`) remains gitignored and is not versioned.

## Breaking changes

- [x] No, it is backward compatible.
- [ ] Yes — see detail and migration guide below.

Backward compatible at the API/contract level. Two source-level import paths moved inside the published artifact, which only affects consumers importing **internal, non-`api/` packages** directly (none of the example consumer code did, and in-repo consumers were updated):

| Before | After |
|---|---|
| `io.github.lauto5.rateLimit.application.ports.out.StateCodec` | `io.github.lauto5.rateLimit.domain.algorithm.StateCodec` |
| `io.github.lauto5.rateLimit.logging.ConsoleLogger` / `.NoOpLogger` | `io.github.lauto5.rateLimit.application.logging.ConsoleLogger` / `.NoOpLogger` |

### Migration guide

- Consumers referencing the old codec/logger package paths must update their imports. Consumers going through `api` / `RateLimit` / `Algorithm` are unaffected.

## Public API impact

No changes to the public `api` contract. The observable change is the package relocation of `StateCodec` and the two concrete loggers (see Breaking changes above), which are internal packages not surfaced through `api`.

## Testing

- [x] New or updated unit tests.
- [ ] New or updated integration tests (`N/A` — no new integration surface).
- [ ] Concurrency tests (`N/A` — no concurrency-logic change).
- [ ] Manual verification (`N/A` — covered by the automated suite).

Validation: the full reactor `mvn clean verify` passes (suite grew from 231 to 237 tests with `PipeDelimitedCodecTest` + two `InMemoryStore` validation tests), and `mvn -f examples/pom.xml clean compile` succeeds against a freshly installed snapshot (`mvn -f rate-limit/pom.xml install -DskipTests`).

## Known risks and considerations

- The `StateCodec` relocation changes the import surface of an internal package; any external consumer importing it directly must update imports (no `api` impact).
- The examples POM parent ties the two example modules together; building an individual module still works (`mvn -f examples/core-inmemory/pom.xml compile`), and the versions must stay in sync with `rate-limit/pom.xml` (documented in both POMs).
- Intermediate commit `1200bcb` is not independently compilable (it is a coordinated refactor stage: the loggers were relocated in the following commit); the branch head is the verified green state.

## Related work left out of this change

- Stages 5-8 of the feature: Tests & Build (`TB-01`..`TB-12`), Documentation (`DC-02`..`DC-06`), Final Cleanup (`CL-01`/`CL-02`) and final verification.
- Optional 🟡 findings not applied: `CS-12`..`CS-15`, `OR-06`..`OR-08`.
- Decisions still pending for later stages: `D2` (camelCase package rename, `feature/package-rename`), `D3` (gitignore policy), `D5` (documentation language policy).
- Candidate features inherited from `docs/ARCHITECTURE.md`: `feature/redis-hardening`, `feature/inmemory-eviction`, `feature/api-v2`, `feature/redis-lua-atomic-operations`, `feature/observability`, `feature/spring-boot-integration`.

## References

- Related documentation: `docs-for-agent-ia/project-cleanup-and-governance/00..06-*.md` and `docs-for-agent-ia/project-cleanup-and-governance/07-audit-and-planned-changes.md`
- Commits: `d14aed4`, `838c754`, `d549d14` (Stage 3); `1200bcb`, `a3c5d7c`, `335eb72` (Stage 4); `f82727a` (OR-05, by the repository owner)
- PR: `<link>` (`N/A` — feature not merged yet)