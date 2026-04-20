# PictureServer Glossary

Shared terminology for the PictureServer project — used in code, documentation, and
communication with AI agents.

---

## Domain Concepts

| Term                | Definition                                                                                                                         |
| ------------------- | ---------------------------------------------------------------------------------------------------------------------------------- |
| **Album**           | A directory on disk that contains pictures and/or sub-albums. Displayed as a grid of tiles.                                        |
| **Sub-album**       | A child directory inside an album; displayed as an album tile in the grid.                                                         |
| **Picture**         | A single image file (`.jpg`, `.jpeg`, `.png`, `.gif`, `.webp`, `.bmp`) inside an album.                                            |
| **Root Directory**  | The top-level filesystem directory configured in `settings.properties`; the server never serves files outside it.                  |
| **Web Path**        | A URL-style path string (e.g. `/family/2024/photo.jpg`) used in API calls and browser URLs.                                        |
| **Filesystem Path** | The absolute path on disk that corresponds to a web path, after safety resolution.                                                 |
| **Session**         | An authenticated login state stored in memory, bound to the user's source IP and User-Agent. Identified by the `PSSESSION` cookie. |
| **Panic Mode**      | A security shutdown state triggered when threat events exceed a configured threshold. All sessions are invalidated.                |
| **Threat Event**    | A security incident tracked by the Panic Monitor (e.g. failed login, path traversal attempt).                                      |

---

## UI / Views

| Term               | Definition                                                                                              |
| ------------------ | ------------------------------------------------------------------------------------------------------- |
| **Login Page**     | The view rendered when the user is not authenticated; contains username/password form.                  |
| **Album Page**     | The main browsing view; shows a grid of album tiles and picture tiles for the current path.             |
| **Picture Page**   | The detail view for a single picture; shows the full-size image and a sidebar of sibling thumbnails.    |
| **Tile**           | A grid item on the Album Page — either an Album Tile or a Picture Tile.                                 |
| **Album Tile**     | A tile representing a sub-album, showing a preview image and the album name.                            |
| **Picture Tile**   | A tile representing a picture, showing a scaled thumbnail.                                              |
| **Album Preview**  | The first image found in a sub-album, used as the visual for its Album Tile.                            |
| **Sidebar**        | A vertical strip of sibling thumbnails on the Picture Page, used for quick navigation between pictures. |
| **Breadcrumb**     | The navigation bar at the top showing the current path as clickable segments.                           |
| **User Menu**      | The hamburger-style menu on the Album/Picture Page with actions: Logout, Shutdown, Delete.              |
| **Confirm Dialog** | A modal overlay that asks the user to confirm a destructive action (delete picture, shutdown).          |

---

## Backend Components (Java)

| Term                     | Definition                                                                                                                           |
| ------------------------ | ------------------------------------------------------------------------------------------------------------------------------------ |
| **Main**                 | Application entry point; wires up all components and starts the HTTP server.                                                         |
| **Settings**             | Immutable configuration record loaded from `settings.properties`; holds port, credentials, root dir, and panic config.               |
| **Request Router**       | Top-level `HttpHandler`; dispatches requests to the Static Asset Handler, the API Router, or serves `index.html` for SPA navigation. |
| **Static Asset Handler** | Serves bundled frontend files (HTML, CSS, JS, SVG) from the classpath under `/assets/`.                                              |
| **API Router**           | Routes authenticated `/api/*` requests to the appropriate API handler.                                                               |
| **Auth API Handler**     | Handles `POST /api/login` and `POST /api/logout`.                                                                                    |
| **Session API Handler**  | Handles `GET /api/session`; returns current authentication status.                                                                   |
| **Album API Handler**    | Handles `GET /api/albums/*`; lists sub-albums and pictures with preview mappings.                                                    |
| **Picture API Handler**  | Handles `GET /api/pictures/*` (metadata + siblings) and `DELETE /api/pictures/*` (move to trash).                                    |
| **Image API Handler**    | Handles `GET /api/images/*`; streams raw image bytes with HTTP cache headers.                                                        |
| **Shutdown API Handler** | Handles `POST /api/shutdown`; gracefully stops the server.                                                                           |
| **Session Manager**      | Creates, validates, and invalidates sessions. Binds each session to source IP and User-Agent.                                        |
| **Path Safety**          | Utility that resolves a web path against the root directory and rejects any traversal attempt.                                       |
| **Panic Monitor**        | Counts threat events per category; shuts down the server when a threshold is breached.                                               |
| **Album Service**        | Business logic for reading album contents: collects sub-album names, picture filenames, and album previews.                          |
| **Picture Service**      | Business logic for a single picture: returns its metadata and the list of sibling filenames.                                         |
| **Cache Helper**         | Computes and validates ETag / Last-Modified headers; returns 304 Not Modified when content is unchanged.                             |
| **Json Helper**          | Wraps Jackson; serialises response objects to JSON and deserialises request bodies with strict validation.                           |
| **Image Types**          | Utility that recognises supported image file extensions and returns their MIME types.                                                |
| **Web Paths**            | Utility for URL path operations: normalise, encode, decode, compute parent path.                                                     |

---

## Frontend Components (JavaScript)

| Term                                       | Definition                                                                                                                                             |
| ------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------ |
| **app.js**                                 | Frontend entry point; initialises the client-side router and imports all views.                                                                        |
| **Router** (`router.js`)                   | Client-side router; switches between views based on the browser URL, manages History API state, and redirects unauthenticated users to the Login Page. |
| **Login View** (`login.js`)                | Renders the Login Page and submits credentials to the Auth API.                                                                                        |
| **Album View** (`album.js`)                | Renders the Album Page; fetches album data and builds the tile grid.                                                                                   |
| **Picture View** (`picture.js`)            | Renders the Picture Page; displays the full-size image and sidebar; handles keyboard navigation and delete.                                            |
| **Breadcrumb Component** (`breadcrumb.js`) | Shared UI component that renders the Breadcrumb from a path string.                                                                                    |
| **Menu Component** (`menu.js`)             | Shared UI component that renders the User Menu.                                                                                                        |
| **API Client** (`api.js`)                  | Thin HTTP client; exports one function per API endpoint with unified error handling.                                                                   |

---

## API Endpoints

| Endpoint               | Method | Auth | Purpose                                 |
| ---------------------- | ------ | ---- | --------------------------------------- |
| `/api/session`         | GET    | No   | Check authentication status             |
| `/api/login`           | POST   | No   | Submit credentials, start session       |
| `/api/logout`          | POST   | Yes  | Invalidate session                      |
| `/api/albums/<path>`   | GET    | Yes  | List sub-albums and pictures for a path |
| `/api/pictures/<path>` | GET    | Yes  | Get picture metadata and sibling list   |
| `/api/pictures/<path>` | DELETE | Yes  | Move picture to trash                   |
| `/api/images/<path>`   | GET    | Yes  | Stream raw image bytes                  |
| `/api/shutdown`        | POST   | Yes  | Shut down the server                    |

---

## API Response Shapes

| Name                | Fields                                                                     |
| ------------------- | -------------------------------------------------------------------------- |
| **AlbumResponse**   | `path`, `albums` (list), `albumPreviews` (map name→URL), `pictures` (list) |
| **PictureResponse** | `path`, `name`, `src` (image URL), `siblings` (list of paths)              |
| **SessionResponse** | `authenticated` (boolean)                                                  |
| **LoginRequest**    | `username`, `password`                                                     |

