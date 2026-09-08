# Project cleanup and governance — Stage 6 (documentation)

| | |
|---|---|
| **Date** | 2026-09-08 |
| **Type** | Chore (documentation accuracy; no functional change) |
| **Module(s)** | `docs`, `CONTRIBUTING.md`, `ARCHITECTURE.md` |
| **Status** | Not merged — branch `feature/project-cleanup-and-governance`; stages 7-8 still pending. Companion to `2026-09-08-project-cleanup-and-governance-stages-5.md`. |
| **Branch** | `feature/project-cleanup-and-governance` |
| **Ticket / issue** | `N/A` |
| **Author(s)** | `N/A` (opencode-assisted session) |

## Summary

Applies the documentation stage of the repository governance feature on top of `8fb70fc` (`DC-02`..`DC-06` in scope): unifies `docs/ARCHITECTURE.md` to a single language (English), fixes the `infraestructure` typos in `CONTRIBUTING.md`, and aligns the documented Java build policy with the real POM (JDK 9+ / Maven 3.9+ enforcer, Java 8 API target via `--release 8`). `DC-02` (`spanish/testing.md`) and `DC-03` (obsolete PREDOC) had already been resolved by the repository owner in `f82727a`.

Commit: `d028dc2`.

## Motivation

The audit (`07`) found documentation that contradicted or misrepresented the repository: `ARCHITECTURE.md` mixed English and Spanish (a Spanish "Etapa 3" merge-decision section inside an English document), `CONTRIBUTING.md` told contributors to use Java 17+ features and claimed the enforcer required Java 17+ — both false for a project that compiles against the Java 8 API with `--release 8` and enforces JDK `[9,)` — and referenced a nonexistent `infraestructure` package. Misleading docs cost contributors time and erode trust in the build rules.

## What changed

- `DC-02`/`DC-03` — already resolved by the repository owner in `f82727a` (removed `docs/spanish/testing.md`, the `docs/Rate limit PREDOC...` file and its image folder); the audit was updated to record that.
- `DC-04` (`d028dc2`) — the Spanish section `## Etapa 3 -- Revisión final y decisión de merge` in `docs/ARCHITECTURE.md` was translated and renamed `## Phase 3 -- Final review and merge decision`, unifying the document in English (content and structure preserved).
- `DC-05` (`d028dc2`) — fixed the `infraestructure` typo in `CONTRIBUTING.md` (`Project Structure` table and the ports rule), matching the real `infrastructure` package.
- `DC-06` (`d028dc2`) — aligned `CONTRIBUTING.md` with the real build: the enforcer note now states **JDK 9+** / Maven 3.9+ (not Java 17+), and the Java coding rule now prescribes Java 8 API compatibility (`--release 8`), explicitly forbidding language/API features newer than Java 8.
- Bonus consistency (`d028dc2`) — updated the stale `debug scripts/` references in `CONTRIBUTING.md` to `debug-scripts/` (the folder was renamed in `f82727a`).

## What did NOT change

- **Content and structure of `ARCHITECTURE.md`**: only the language of the audited section changed; tables, feature list and decision text were preserved.
- **Public API, behavior or build configuration**: documentation-only change; no Java/Maven files touched.
- **The governed docs (`docs-for-agent-ia/`) remain gitignored** and are updated in place, not versioned.

## Breaking changes

- [x] No, it is backward compatible.
- [ ] Yes — see detail and migration guide below.

`N/A` — documentation-only.

## Public API impact

No changes to the public API.

## Testing

- [ ] New or updated unit tests (`N/A` — no code change).
- [ ] New or updated integration tests (`N/A`).
- [ ] Concurrency tests (`N/A`).
- [x] Manual verification — reviewed the rendered markdown for language consistency (no Spanish accents/words left in `ARCHITECTURE.md`) and cross-checked the corrected statements against `rate-limit/pom.xml` and `README.md`.

## Known risks and considerations

- The obsolete "Phase 3" snapshot in `ARCHITECTURE.md` (test counts, feature status) was kept as-is and only translated; the audit's intent (`DC-04`) was language consistency, not re-validating that snapshot.
- `DC-01` (D3) — whether to version `docs-for-agent-ia/` — remains an open user decision; until resolved, governance docs stay local-only.

## Related work left out of this change

- `DC-01` (D3, `docs-for-agent-ia/` gitignore policy) — user decision pending.
- Optional 🟡 findings not applied: `DC-07` (documentation language policy by audience, D5), `DC-08` (README `Persistence` API table incomplete), `DC-09` (duplicated "Project Structure" between README and CONTRIBUTING).
- Stages 7-8 of the feature: Final Cleanup (`CL-01`, `CL-02`, unused imports) and final verification.

## References

- Related documentation: `docs-for-agent-ia/project-cleanup-and-governance/00..06-*.md` and `docs-for-agent-ia/project-cleanup-and-governance/07-audit-and-planned-changes.md`
- Commits: `d028dc2` (DC-04/DC-05/DC-06), `f82727a` (DC-02/DC-03, by the repository owner)
- PR: `<link>` (`N/A` — feature not merged yet)