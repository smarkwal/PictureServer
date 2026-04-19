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

```text
src/main/java/net/markwalder/pictureserver/
  Logger.java                          # Logging utility
  Main.java                            # Entry point, HttpServer bootstrap
  auth/
    SessionManager.java                # Server-side session map, cookie name PSSESSION
  config/
    Settings.java                      # Record: rootDirectory, port, username, password, panic
    SettingsLoader.java                # Reads settings.properties from CWD
  security/
    PanicMonitor.java                  # Threat detection and panic mode
    ThreatEvent.java                   # Threat event types enum
  web/
    ImageTypes.java                    # Image MIME type mapping and extension checks
    PathSafety.java                    # resolveSafePath(), normalizeWebPath(), parentWebPath()
    RequestRouter.java                 # Top-level HttpHandler; calls panicMonitor.checkPath(), routes by prefix
    StaticAssetHandler.java            # Serves classpath /assets/ files (index.html, app.css, JS, icon.svg)
    api/
      ApiRouter.java                   # Routes /api/*; auth guard for protected endpoints
      AlbumApiHandler.java             # GET /api/albums/{path} → JSON album listing
      AuthApiHandler.java              # POST /api/login, POST /api/logout
      ImageApiHandler.java             # GET /api/images/{path} → binary image stream
      JsonHelper.java                  # Shared strict ObjectMapper, sendJson(), readJson(), readCookie()
      PictureApiHandler.java           # GET /api/pictures/{path}, DELETE /api/pictures/{path}
      SessionApiHandler.java           # GET /api/session → { authenticated: bool }
      ShutdownApiHandler.java          # POST /api/shutdown
    service/
      AlbumService.java                # listAlbum(): albums, albumPreviews, pictures
      PictureService.java              # getPictureInfo(): sibling list
src/main/resources/assets/
  index.html                           # SPA shell — served for / and all non-API, non-asset paths
  app.css                              # Stylesheet
  icon.svg                             # Favicon
  app.js                               # Entry point; imports all views/components, calls router.init()
  router.js                            # History API client router (navigate, popstate, session check)
  api.js                               # fetch() wrappers returning JSON; throws ApiError on non-2xx
  views/
    login.js                           # Login form view
    album.js                           # Album grid view (tiles, breadcrumb, menu)
    picture.js                         # Picture detail view (sidebar, delete, prev/next)
  components/
    breadcrumb.js                      # Clickable breadcrumb from a path string
    menu.js                            # Hamburger <details> menu
src/test/java/net/markwalder/pictureserver/
  config/SettingsLoaderTest.java       # Unit tests for settings parsing/validation
  security/PanicMonitorTest.java       # Unit tests for panic monitor
  web/service/AlbumServiceTest.java    # Unit tests for album listing logic
  web/service/PictureServiceTest.java  # Unit tests for picture sibling logic
```

## Key Conventions

- **Authentication**: Plain-text username/password comparison against `settings.properties`. Session persisted in `SessionManager` with a browser-session cookie (`PSSESSION`; `HttpOnly; SameSite=Strict`).
- **Path safety**: All URL paths are resolved against the configured root via `resolveSafePath()` in `PathSafety`. Never bypass or weaken this check.
- **Frontend**: Single-Page Application using vanilla JS ES modules. The server serves `index.html` as a catch-all; the JS router handles view rendering via the History API.
- **API**: All data endpoints are under `/api/*` and return JSON. Static assets are served from `/assets/*`.
- **JSON deserialization**: All incoming JSON is parsed through `JsonHelper`'s strict `ObjectMapper` — unknown properties, null primitives, and scalar coercion all throw `JsonProcessingException`, which triggers an `INVALID_REQUEST` panic event.
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

| Library                                           | Context7 ID                                   |
| ------------------------------------------------- | --------------------------------------------- |
| Java SE 25 / JDK 25 (standard library, APIs)      | `/websites/oracle_en_java_javase_25`          |
| Gradle 9.4.1 (Kotlin DSL, tasks, dependencies)    | `/websites/gradle_9_4_1`                      |
| JUnit 6 / Jupiter (tests, assertions, extensions) | `/junit-team/junit-framework`                 |
| Jackson Databind 2.19.0 (JSON serialization)      | `/fasterxml/jackson-databind`                 |
| Mockito 5 (mocking)                               | `/mockito/mockito`                            |
| AssertJ 3 (fluent assertions)                     | `/assertj/assertj`                            |
