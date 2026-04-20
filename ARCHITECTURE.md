# Architecture

## Overview

Picture Server is a single-process Java application with an embedded JDK HTTP server.
It serves a password-protected, album-style web UI over plain HTTP and reads configuration from
`settings.properties` in the current working directory.

## Runtime Components

- `Main` bootstraps configuration, security services, API router, and `HttpServer`.
- `RequestRouter` is the top-level HTTP handler and dispatches by URL prefix.
- `StaticAssetHandler` serves classpath assets from `/assets/*` and the SPA shell `index.html`.
- `ApiRouter` handles `/api/*` endpoint routing and enforces authentication where required.
- `SessionManager` manages in-memory browser-session authentication (`PSSESSION` cookie).
- `PanicMonitor` detects suspicious behavior and can trigger panic mode shutdown.

## Request Routing

- `/assets/*` -> static assets from `src/main/resources/assets/`
- `/api/*` -> JSON and image API handlers
- all other paths -> SPA entry page (`index.html`), then frontend router handles navigation

## Source Layout

The structure below is intentionally package-level to avoid frequent documentation churn.
Use file search for class-level discovery.

```text
src/main/java/net/markwalder/pictureserver/
  auth/      # session and cookie lifecycle
  config/    # settings model + properties loading/validation
  security/  # threat detection and panic controls
  web/       # HTTP routing, path safety, cache helpers, mime helpers
    api/     # REST endpoint handlers and API router
    service/ # filesystem-backed album/picture business logic

src/main/resources/assets/
  views/       # login, album, picture screens
  components/  # reusable UI pieces (menu, breadcrumb)
  app.js       # frontend entry point
  router.js    # SPA navigation
  api.js       # browser API client
  app.css      # styles
  index.html   # SPA shell

src/test/java/net/markwalder/pictureserver/
  auth/
  config/
  security/
  web/
    api/
    service/
```

## REST API

All data endpoints are under `/api/*`.

### Authentication Model

- Authentication uses username/password from `settings.properties`.
- Successful login sets cookie `PSSESSION` with `HttpOnly` and `SameSite=Strict`.
- Most endpoints require an authenticated session tied to source IP and user agent.

### Endpoint Summary

| Method | Path                   | Auth required | Description                                     |
| ------ | ---------------------- | ------------- | ----------------------------------------------- |
| GET    | `/api/session`         | no            | Returns `{ authenticated: boolean }`            |
| POST   | `/api/login`           | no            | Validates credentials and sets session cookie   |
| POST   | `/api/logout`          | no            | Clears session and expires cookie               |
| GET    | `/api/albums/{path}`   | yes           | Returns album listing and preview image URLs    |
| GET    | `/api/pictures/{path}` | yes           | Returns picture metadata and sibling navigation |
| DELETE | `/api/pictures/{path}` | yes           | Moves the picture to system trash               |
| GET    | `/api/images/{path}`   | yes           | Streams image bytes with cache headers          |
| POST   | `/api/shutdown`        | yes           | Requests graceful server shutdown               |

### Common Error Responses

| Status | Typical meaning                                   |
| ------ | ------------------------------------------------- |
| 400    | Invalid JSON request payload                      |
| 401    | Not authenticated                                 |
| 403    | Forbidden (path traversal or image access denied) |
| 404    | Resource not found                                |
| 405    | Method not allowed                                |
| 500    | Server error                                      |

### Response Notes

- API responses are JSON unless the endpoint streams binary image data.
- `/api/images/{path}` sets `ETag`, `Last-Modified`, and supports `304 Not Modified`.
- Paths are normalized/encoded using `WebPaths` and resolved safely with `PathSafety`.

## Design Constraints

- No web framework (no Spring/Jakarta/Quarkus).
- No database; runtime state is in memory.
- Filesystem path traversal protection must remain enforced through `PathSafety.resolveSafePath()`.
- Frontend is vanilla ES modules served as static assets.
