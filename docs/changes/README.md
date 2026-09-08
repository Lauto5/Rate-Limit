# Changes — Conventions

This folder documents significant project changes: one file per change, in a consistent format, designed so that anyone (or any agent) can understand what changed, why, and what impact it had, without having to reconstruct it from the commit history.

## When to create a change document

Create a file in `changes/` when the change:

- modifies the public API (adds, removes, or changes the behavior of something exposed);
- introduces or modifies an architectural decision;
- changes observable system behavior (not just style or form);
- requires a library consumer to do something when upgrading (breaking change or migration);
- is the result of a complete feature (for example, when closing `feature/multi-module-architecture` or `feature/project-cleanup-and-governance`).

Do not create a change document for:

- style fixes, naming, or internal reorganization without observable impact;
- trivial changes already sufficiently described by the commit message;
- work in progress within a branch (the document is created when the change is finished and ready to merge, not before).

## File naming

```text
YYYY-MM-DD-short-slug-in-kebab-case.md
```

Examples:

```text
2026-03-14-redis-store-atomic-transactions.md
2026-04-02-rate-limit-store-api-v2.md
2026-05-20-project-cleanup-and-governance.md
```

- The date is the date of the merge to `main`, not the start of the work.
- The slug describes the change, not the branch or the ticket number (the ticket goes inside the document, not in the file name).

## One document per change

Each file documents **one** coherent change, even if it spanned multiple commits or several internal stages (such as the stages of a large feature). Do not create one file per commit, and do not mix several unrelated changes in the same document.

## Structure

Always use `TEMPLATE.md` as the starting point. Do not omit sections: if a section does not apply, leave it explicitly as `N/A` with a brief reason, instead of deleting it. This keeps the documents comparable with each other.

## Index

This folder does not keep an additional manual index; the file listing ordered by name (which starts with the date) already works as a chronological index.