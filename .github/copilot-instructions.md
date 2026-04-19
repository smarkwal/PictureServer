# Picture Server – Copilot Instructions

## Project Overview

A Java application that hosts an embedded HTTP server for browsing pictures stored on disk via a password-protected, album-style web UI. Configuration is loaded from `settings.properties` in the current working directory.

## Tech Stack

- **Language**: Java 25
- **Build**: Gradle 9+ with Kotlin DSL (`build.gradle.kts`)
- **HTTP server**: Plain JDK `com.sun.net.httpserver.HttpServer` — no web frameworks
- **Configuration**: Java `java.util.Properties` (no external libraries)
- **Tests**: JUnit 6 (Jupiter) via `@Test`, `@TempDir`; no mocking libraries

## Source Layout

```text
src/main/java/net/markwalder/pictureserver/
  Main.java                       # Entry point, HttpServer bootstrap
  config/
    Settings.java                 # Record: rootDirectory, port, password
    SettingsLoader.java           # Reads settings.properties from CWD
  auth/
    SessionManager.java           # Server-side session map, cookie name PSSESSION
  web/
    PictureServerHandler.java     # Routes, auth guard, path safety, album/image logic
    HtmlRenderer.java             # Inline HTML rendering (no template engine)
    ui/
      UiComponent.java            # Simple UI component contract
      HtmlEscaper.java            # HTML escaping utility
      BreadcrumbComponent.java    # Breadcrumb rendering
      AlbumGridComponent.java     # Album/picture grid rendering
      UserMenuComponent.java      # User menu container rendering
      MenuLinkItemComponent.java  # Menu link item rendering
      MenuDialogItemComponent.java # Menu action button rendering
      ConfirmationDialogComponent.java # Reusable confirmation dialog rendering
src/test/java/net/markwalder/pictureserver/
  config/SettingsLoaderTest.java  # Unit tests for settings parsing/validation
```

## Key Conventions

- **Authentication**: Plain-text password comparison against `settings.properties`. Session persisted in `SessionManager` with a browser-session cookie (`PSSESSION`).
- **Path safety**: All URL paths are resolved against the configured root via `resolveSafePath()` in `PictureServerHandler`. Never bypass or weaken this check.
- **HTML**: Rendered inline in `HtmlRenderer` as Java text blocks — no external template engine.
- **Classes**: `final` with private constructors for utility/service classes; records for data-only types (`Settings`).
- **No external frameworks**: Do not introduce Spring, Jakarta EE, Quarkus, or any IoC container.
- **No database**: This project is intentionally stateless (no JPA, JDBC, H2, etc.).

## Setup Reference

- For user-facing setup, configuration examples, and run/test commands, use `README.md` as the canonical source.
- Keep this file focused on agent behavior, architecture constraints, and codebase conventions.

## Testing Guidelines

- Use `@TempDir` for filesystem fixtures — never hardcode real paths.
- Assert on `IllegalStateException` messages for settings validation tests.
- Add tests under `src/test/java/net/markwalder/pictureserver/` mirroring the main package structure.

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
