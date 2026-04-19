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

## Context7 Libraries

When looking up API docs or code examples for the libraries used in this project, use these Context7 library IDs:

| Library | Context7 ID |
| ------- | ----------- |
| Java SE 25 / JDK 25 (standard library, APIs) | `/websites/oracle_en_java_javase_25` |
| Gradle 9.4.1 (Kotlin DSL, tasks, dependencies) | `/websites/gradle_9_4_1` |
| JUnit 6 / Jupiter (tests, assertions, extensions) | `/junit-team/junit-framework` |
