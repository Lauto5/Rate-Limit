<!--
Copy this file as docs/changes/YYYY-MM-DD-short-slug.md
See docs/changes/README.md for the naming and when-to-create conventions.
Do not delete sections: if not applicable, leave "N/A" with a brief reason.
-->

# <Short descriptive title of the change>

| | |
|---|---|
| **Date** | YYYY-MM-DD |
| **Type** | Feature / Fix / Refactor / Breaking change / Deprecation / Chore |
| **Module(s)** | `rate-limit-core` / `rate-limit-inmemory` / `rate-limit-redis` / `examples` / ... |
| **Status** | Merged / Superseded by `<link>` |
| **Branch** | `feature/...` |
| **Ticket / issue** | `#000` or `N/A` |
| **Author(s)** | |

## Summary

One or two sentences describing the change for someone without prior context. It must be readable on its own and understandable.

## Motivation

Why this change was made. What problem it solved, what limitation the system had before, or what need originated it. Do not describe the "what" here (that goes in the next section), only the "why".

## What changed

Concrete description of the change. Prefer a list over a long paragraph when the change has several parts.

- ...
- ...
- ...

If the change was significant or executed in stages, reference the corresponding feature documentation (for example, in `docs-for-agent-ia/`) instead of repeating all the detail here.

## What did NOT change

Explicit, especially for large changes or refactors: what was intentionally kept the same (architecture, public API, behavior, etc.). It helps readers not assume more impact than there actually was.

## Breaking changes

Does this change break compatibility with previous versions?

- [ ] No, it is backward compatible.
- [ ] Yes — see details and migration guide below.

If breaking:

| Before | After |
|---|---|
| | |

### Migration guide

Concrete steps a library consumer must take to adapt to this change. If not applicable, `N/A`.

## Public API impact

Describe any addition, change, or removal in the public API. If there is no impact, state explicitly `No changes to the public API`.

```java
// Before (if applicable)

// After (if applicable)
```

## Testing

How the change was validated.

- [ ] New or updated unit tests.
- [ ] New or updated integration tests.
- [ ] Concurrency tests (if applicable).
- [ ] Manual verification (describe briefly if applicable).

## Known risks and considerations

Any limitation, consciously accepted technical debt, or residual risk left after this change. If there is nothing relevant, state `N/A`.

## Related work left out of this change

If this work identified future improvements or features that were explicitly left out of scope, list them here with a reference to where they are documented (for example, the "candidate features" section of the corresponding execution plan).

- ...

## References

- Related documentation: `docs/...`, `docs-for-agent-ia/...`
- PR: `<link>`
- Issue/ticket: `<link>`