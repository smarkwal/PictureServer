# Picture Server – Copilot Instructions

## Project Overview

A Java application that hosts an embedded HTTP server for browsing pictures stored on disk via a password-protected, album-style web UI. Configuration is loaded from `settings.properties` in the current working directory.

## Tech Stack

- **Language**: Java 25
- **Build**: Gradle 9+ with Kotlin DSL (`build.gradle.kts`)
- **HTTP server**: Plain JDK `com.sun.net.httpserver.HttpServer` — no web frameworks
- **JSON**: Jackson Databind 2.19.0 — strict deserialization, `@JsonCreator`/`@JsonProperty` on all request POJOs
- **Frontend**: Vanilla JS ES modules (no framework); static assets served from classpath `/assets/`
- **Configuration**: Java `java.util.Properties` (no external libraries)
- **Tests**: JUnit 6 (Jupiter) via `@Test`, `@TempDir`; Mockito 5 for mocking; AssertJ 3 for assertions

## Source Layout

Source layout and endpoint details are documented in `ARCHITECTURE.md`.

Keep this file stable and avoid file-by-file listings here so routine class additions/removals do not require updates.

## Key Conventions

- **Authentication**: Plain-text username/password comparison against `settings.properties`. Session persisted in `SessionManager` with a browser-session cookie (`PSSESSION`; `HttpOnly; SameSite=Strict`).
- **Path safety**: All URL paths are resolved against the configured root via `resolveSafePath()` in `PathSafety`. Never bypass or weaken this check.
- **Frontend**: Single-Page Application using vanilla JS ES modules. The server serves `index.html` as a catch-all; the JS router handles view rendering via the History API.
- **API**: All data endpoints are under `/api/*` and return JSON. Static assets are served from `/assets/*`.
- **JSON deserialization**: All incoming JSON is parsed through `JsonHelper`'s strict `ObjectMapper` — unknown properties, null primitives, and scalar coercion all throw `JsonProcessingException`, which triggers an `INVALID_REQUEST` panic event.
- **Classes**: `final` with private constructors for utility/service classes; records for data-only types (`Settings`).
- **No external frameworks**: Do not introduce Spring, Jakarta EE, Quarkus, or any IoC container.
- **No database**: This project is intentionally stateless (no JPA, JDBC, H2, etc.).

## Code Style

### Java Import Order

- Static imports first, then one blank line, then all non-static imports.
- Both groups are sorted alphabetically, case-sensitively (uppercase before lowercase, i.e. `Z` before `a`).
- No sub-grouping within each block (no blank lines between packages, no separation of `java.*` from third-party).

```java
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.List;
import net.markwalder.pictureserver.config.Settings;
import org.junit.jupiter.api.Test;
```

### Formatting

After modifying any source file, run `./gradlew spotlessApply` to enforce consistent formatting (import order, trailing whitespace, final newline). The `spotlessCheck` task — wired into `check` — will fail the build if files are out of compliance.

### Line Length

Avoid line-continuation breaks. When a statement is too long to fit on one line, extract intermediate values into named local variables rather than breaking the statement across lines. Exception: fluent APIs (streams, builders) may span multiple lines — one method call per line, aligned to the opening expression.

### Inline Comments

Any production method body that contains two or more distinct logical phases must have a section label comment before each phase. A **phase** is a group of statements that share a single purpose; when one purpose ends and another begins, a comment is required. Line count is irrelevant — a 6-line method with three phases needs three labels; a 15-line method that does one thing needs none.

Comments are section *labels*, not prose:

- Use an **imperative verb phrase**: `// Validate input`, `// Build response`, `// Send error`.
- Place the comment on its own line **immediately before** the phase it labels — never at the end of a line. Always leave a blank line above the comment (except at the very start of a method body).
- Do **not** comment self-evident single statements where the identifier names already convey the meaning (e.g., `list.sort(comparator)` needs no comment).
- Do **not** paraphrase what the code literally does — the comment must add meaning the reader could not instantly derive from the identifiers alone. If you cannot write a comment that adds meaning, the code is already clear enough.
- If a phase is difficult to label concisely, that is a signal to extract a helper method instead.

This rule applies to **production code only**. Test methods follow the AAA pattern (`// Arrange`, `// Act`, `// Assert`) defined in the Testing Guidelines above.

## Setup Reference

- For user-facing setup, configuration examples, and run/test commands, use `README.md` as the canonical source.
- Keep this file focused on agent behavior, architecture constraints, and codebase conventions.

## Testing Guidelines

- Use `@TempDir` for filesystem fixtures — never hardcode real paths.
- Assert on `IllegalStateException` messages for settings validation tests.
- Add tests under `src/test/java/net/markwalder/pictureserver/` mirroring the main package structure.
- Follow the **AAA pattern** in every test method: separate the body into three sections with inline comments `// Arrange`, `// Act`, and `// Assert`. Omit `// Arrange` only when there is genuinely nothing to set up. Use `// Act & Assert` when the act and assert cannot be meaningfully separated (e.g., a single `assertEquals` with no intermediate variable).

## Commit Message Conventions

Every commit message subject line must follow this structure:

```text
<Type>: [<Area>:] <Message>
```

### Type

Use one of two groups:

**User-facing changes** (affect features, UI, or behavior visible to end users):
- `Feature` — new functionality
- `Change` — modification to existing behavior
- `Bugfix` — correction of a defect

**Internal-only changes** (no effect on production behavior or user experience):
- `Code` — refactoring, formatting, or non-test source changes
- `Tests` — adding or modifying tests
- `Docs` — documentation updates
- `Project` — build, CI, dependencies, tooling

### Area (optional)

For user-facing types, append a concise area name after the type to identify the affected feature or component (e.g., `Authentication`, `Album Grid`, `Breadcrumbs`, `Picture Page`, `Logout Action`). Omit the area when the change is cross-cutting or no single area dominates. Do not use Java class names, file names, or internal IDs as area names — use terms familiar to end users.

For internal types (`Code`, `Tests`, `Docs`, `Project`), the area is rarely needed and should be omitted unless it aids clarity.

### Message

- Write in imperative mood: "Add …", "Fix …", "Upgrade …" (not "Added …" or "Adding …").
- The entire subject line (type + area + message) should not exceed 120 characters.
- Do not reference Java class names, file names, or internal IDs in user-facing commit messages.

### Examples

```
Feature: Picture Page: Add "Next" and "Back" buttons
Change: Authentication: Lock user after 3 failed login attempts
Bugfix: Logout Action: Fix layout of "Logout" button
Tests: Implement unit tests for UrlEncoder
Code: Reformat Java code for web UI components
Docs: Add instructions for commit messages
Project: Upgrade to latest Gradle version
```

### AI agent guidance

After completing work on a task, always suggest a commit message following these conventions before finishing.
If the work in a session covers multiple tasks, suggest a commit message for each task.

## Context7 Libraries

When looking up API docs or code examples for the libraries used in this project, use these Context7 library IDs:

| Library                                           | Context7 ID                          |
| ------------------------------------------------- | ------------------------------------ |
| Java SE 25 / JDK 25 (standard library, APIs)      | `/websites/oracle_en_java_javase_25` |
| Gradle 9.4.1 (Kotlin DSL, tasks, dependencies)    | `/websites/gradle_9_4_1`             |
| JUnit 6 / Jupiter (tests, assertions, extensions) | `/junit-team/junit-framework`        |
| Jackson Databind 2.19.0 (JSON serialization)      | `/fasterxml/jackson-databind`        |
| Mockito 5 (mocking)                               | `/mockito/mockito`                   |
| AssertJ 3 (fluent assertions)                     | `/assertj/assertj`                   |
