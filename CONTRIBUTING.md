# Contributing to Rate Limit

Thank you for considering a contribution to Rate Limit. This document outlines the guidelines and workflow for contributing.

---

## Table of Contents

- [Code of Conduct](#code-of-conduct)
- [Getting Started](#getting-started)
- [Development Setup](#development-setup)
  - [Debug Scripts (Python)](#debug-scripts-python)
- [Project Structure](#project-structure)
- [How to Contribute](#how-to-contribute)
  - [Reporting Bugs](#reporting-bugs)
  - [Suggesting Features](#suggesting-features)
  - [Submitting Code](#submitting-code)
- [Coding Standards](#coding-standards)
- [Testing](#testing)
- [Commit Messages](#commit-messages)
- [Pull Request Process](#pull-request-process)

---

## Code of Conduct

Be respectful, constructive, and professional. We are here to build something useful together.

---

## Getting Started

1. **Fork** the repository on GitHub.
2. **Clone** your fork locally:
   ```bash
   git clone https://github.com/<your-username>/Rate-Limit.git
   ```
3. **Create a branch** for your change:
   ```bash
   git checkout -b feature/my-new-feature
   ```
4. **Make your changes** following the coding standards below.
5. **Run tests** to verify nothing is broken.
6. **Push** and open a Pull Request.

---

## Development Setup

### Prerequisites

- **Java** The whole project (main and tests) compiles against the Java 8 API via `--release 8`, so a **JDK 9+** is required to build. The produced library runs on **Java 8+**.
- **Maven** 3.9+ <!-- TODO: Confirm minimum Maven version -->

### Terminal Workflow

The `pom.xml` is tuned for terminal workflows:

```bash
# Compile (main and tests against the Java 8 API surface)
mvn clean compile

# Run all tests (failing tests are retried once; test stdout goes to
# target/surefire-reports to keep the console clean)
mvn test

# If you change the Java version / compiler config in the pom (release),
# always rebuild the full target to avoid stale classes:
mvn clean test

# Run a single test class
mvn test -Dtest=TokenBucketAlgorithmImplUnitTest

# Run a single test method
mvn test -Dtest=TokenBucketAlgorithmImplUnitTest#someTestCase

# Verify everything: tests + JaCoCo coverage report at
# target/site/jacoco/index.html
mvn verify

# Quick terminal coverage summary (CSV/XML in target/site/jacoco)
mvn jacoco:report

# Run an example main class directly from the terminal
mvn compile exec:java -Dexec.mainClass="com.example.MyMain" -Dexec.args="--arg1 value"
```

> The Maven Enforcer pins the build to **JDK 9+** / Maven 3.9+ (the reactor compiles against the Java 8 API with `--release 8`). If `mvn` refuses to run, check `mvn -version`.

### Debug Scripts (Python)

To avoid typing the multi-command workflows above, the repository ships a Python helper at
`debug-scripts/run.py`. It wraps every project command (compile, test, verify, install,
examples, git status) behind one small CLI, so contributors can run the whole pipeline with a
single call.

Requirements:

- **Python 3** (standard library only, no extra packages to install).
- **Maven** on the `PATH`.
- **Docker** only for the Redis integration tests (the script warns you if it is missing).

Run it from the `debug-scripts/` folder:

```bash
python3 run.py all      # full pipeline (see below)
python3 run.py --help   # every command and option
python3 run.py test -m redis
```

| Command | What it runs |
|---|---|
| `python3 run.py env` | Shows the Java / Maven / Python versions and whether Docker is available |
| `python3 run.py status` | `git status --short` plus the last 10 commits |
| `python3 run.py compile` | Compiles the reactor (`core`, `inmemory`, `redis`) and both example projects |
| `python3 run.py test` | `mvn test` for the whole reactor |
| `python3 run.py test -m <module>` | Tests a single module (`core`, `inmemory`, or `redis`); builds dependencies with `-am` |
| `python3 run.py verify` | Full `mvn clean verify` (tests + JaCoCo coverage report) |
| `python3 run.py install` | Installs the modules into the local Maven repository (skips tests) |
| `python3 run.py examples` | `mvn clean package` for `examples/core-inmemory` and `examples/core-redis` |
| `python3 run.py all` | Complete pipeline: `env` → `install` → `examples` → `clean verify` |

Every command prints the exact Maven command it runs. Pass `--dry-run` to preview commands
without executing them:

```bash
python3 run.py --dry-run all
```

> Before opening a Pull Request, run `python3 run.py all` and confirm it ends green.

---

## Project Structure

The project follows **hexagonal architecture** (ports and adapters):

| Layer | Package | Purpose |
|---|---|---|
| **Public API** | `api` | Factory classes (`Algorithm`, `Persistence`) |
| **Application** | `application` | Orchestration, adapters, ports, result mapping |
| **Domain** | `domain` | Core logic: algorithms, state, policies, models |
| **Infrastructure** | `infrastructure` | Adapters: stores, `ConsoleLogger`, `NoOpLogger` |

When adding a new feature, place it in the correct layer. The domain layer must have **no dependencies** on infrastructure or external frameworks.

---

## How to Contribute

### Reporting Bugs

Open an issue with:

- A clear, descriptive title.
- Steps to reproduce the problem.
- Expected vs. actual behavior.
- Java version and OS information.

### Suggesting Features

Open an issue describing:

- The problem you want to solve.
- Your proposed solution.
- Alternatives you considered.

### Submitting Code

1. Ensure your code follows the [Coding Standards](#coding-standards).
2. Add or update tests for your changes.
3. Run the full test suite and confirm it passes.
4. Open a Pull Request with a clear description of what changed and why.

---

## Coding Standards

### General

- Follow existing code style and conventions in the project.
- Use descriptive names. Avoid abbreviations unless they are widely understood.
- Keep methods short and focused. Each method should do one thing.
- Prefer immutability. Use `final` fields and avoid mutable state where possible.

### Java

- The reactor compiles against the Java 8 API (`--release 8`): do not use language or API features newer than Java 8 (records, sealed classes, pattern matching, `var`, `List.of`, ...). Build on a JDK 9+ so the `--release 8` flag is available.
- Use `Optional` for nullable return values in public APIs.
- Validate inputs at constructor boundaries and throw `IllegalArgumentException` for invalid arguments.
- Use `java.time` API for all date/time operations. Do not use `System.currentTimeMillis()`.

### Architecture

- **Domain layer** (`domain/`) must not depend on infrastructure or application layers.
- **Ports** (`application/ports/`) define contracts. Implementations belong in `infrastructure/`.
- Algorithms must implement `RateLimitAlgorithm<S, P>` with typed state and policy generics.
- State classes must be immutable value objects.

### Logging

- The `Logger` port lives in `application/ports/out/`. Use it for all diagnostic output; never print to `System.out`/`System.err` directly.
- Add logging at the **application and infrastructure layers** only. The **domain layer** must remain free of logging concerns.
- When adding a constructor or factory that accepts a `Logger`, keep a backward-compatible overload that defaults to `NoOpLogger.getInstance()`.
- Use appropriate levels: `DEBUG` for internals/details, `INFO` for normal decisions, `WARN` for denials and retries, `ERROR` for failures.

---

## Testing

- All new features must include unit tests.
- Use JUnit 5 (`@Test`, `@BeforeEach`, assertions).
- Prefer descriptive test method names (e.g., `requestShouldBeDeniedAfterLimitIsReached`).
- Use test doubles from the `testdoubles` package for stubs and fakes.
- Use `Clock.fixed()` for deterministic time-based testing.

### Running Tests

```bash
mvn test
```

---

## Commit Messages

Follow this format:

```
Type : Short description (imperative mood)
```

**Types:**

| Type | Usage |
|---|---|
| `Add` | New feature or file |
| `Fix` | Bug fix or correction |
| `Refactor` | Code restructuring without behavior change |
| `Update` | Enhancement to existing functionality |
| `Remove` | Removing code or files |
| `Docs` | Documentation changes |
| `Test` | Adding or modifying tests |

**Examples:**

```
Add : sliding window counter algorithm implementation
Fix : corrected remaining count in FixedWindowAlgorithm
Refactor : extracting common state initialization logic
Docs : adding architecture documentation
Test : adding edge case tests for TokenBucketAlgorithm
```

---

## Pull Request Process

1. **Title:** Use the same `Type : description` format as commits.
2. **Description:** Explain what the PR does and why. Reference related issues if applicable.
3. **Scope:** Keep PRs focused. One feature or fix per PR is preferred.
4. **Tests:** All existing tests must pass. New code must include tests.
5. **Review:** Be open to feedback and willing to make changes.
6. **Merge:** The maintainer will merge once the PR is approved and CI passes <!-- TODO: Add CI/CD pipeline status once configured -->.

---

## Questions?

If you have questions about contributing, feel free to open an issue with the `question` label or reach out <!-- TODO: Add contact method (email, Discord, etc.) -->.
