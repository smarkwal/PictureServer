---
name: dead-code-cleanup
description: 'Audit and remove dead code from the codebase. Use for: finding unused Java parameters, fields, methods, imports; unused CSS classes/rules; unused JS exports or functions; removing leftover symbols after refactors; cleaning up after feature removal. Produces a report first, then removes each item after confirmation.'
argument-hint: 'Optional: scope to a specific file, package, or symbol type (e.g. "CSS only", "Java only", "api.js")'
---

# Dead Code Cleanup

## When to Use

- After a refactor that removed a feature or changed an API
- When the IDE reports "unused parameter", "unused import", or similar hints
- Periodic codebase hygiene passes
- After a UI redesign that may leave orphaned CSS rules or JS functions

## Source Layout Reminder

```
src/main/java/net/markwalder/pictureserver/
  web/
    RequestRouter.java, StaticAssetHandler.java
    ImageTypes.java, PathSafety.java
    api/   (ApiRouter, *ApiHandler, JsonHelper)
    service/  (AlbumService, PictureService)

src/main/resources/assets/
  index.html
  app.css
  app.js, router.js, api.js
  views/   (login.js, album.js, picture.js)
  components/  (breadcrumb.js, menu.js)
```

There are **no** HTML-generating Java classes. All HTML is produced by the JS files in `src/main/resources/assets/`.

## Procedure

### 1. Inventory source files

Collect all files to audit based on the requested scope (default: full codebase).

### 2. Audit Java symbols

For each Java source file check for:

- **Unused imports** — imported but no direct usage in the file body
- **Unused parameters** — declared in a method or constructor signature but never read
- **Unused local variables** — assigned but never read
- **Unused private fields** — declared but never referenced outside the constructor
- **Unused private methods** — declared but never called from within the class
- **Dead branches** — conditions that can never be true given the types involved

Use grep for exact-text searches and broader symbol usage checks across `src/main/java/`.

### 3. Audit CSS

CSS lives in `src/main/resources/assets/app.css`.

Extract all class selectors:

```bash
grep -oE '\.[a-z][a-z0-9_-]+' src/main/resources/assets/app.css | sort -u
```

For each CSS class, check usage across **all JS and HTML assets** (not Java sources):

```bash
grep -r "class-name" src/main/resources/assets/
```

Flag any class with zero hits as a candidate for removal.

### 4. Audit JavaScript

For each JS file in `src/main/resources/assets/`:

- **Unused exports** — symbols declared with `export` but not imported in any other module
- **Unused functions** — functions defined but never called within the module or imported elsewhere
- **Unreachable code** — code after an unconditional `return` or in branches that can never be reached
- **Dead imports** — `import` statements where the imported binding is never used

Check cross-module usage with:

```bash
grep -r "symbolName" src/main/resources/assets/
```

### 5. Report findings

Present a table of candidates grouped by type before making any changes:

| Type             | Location                  | Symbol            | Reason                           |
| ---------------- | ------------------------- | ----------------- | -------------------------------- |
| Unused import    | `AlbumApiHandler.java:12` | `java.util.Set`   | Never used in file body          |
| Unused CSS rule  | `app.css:239`             | `.nav-spacer`     | No JS or HTML uses this class    |
| Unused JS export | `api.js:42`               | `export function` | Not imported by any other module |

Ask the user to confirm before proceeding with removals.

### 6. Remove confirmed items

- Remove unused parameters from signatures **and** all call sites simultaneously.
- Remove unused CSS rules including their full rule block.
- Remove unused imports individually.
- Remove unused JS exports/functions and their usages.
- Never remove a symbol that is part of a public API without explicit confirmation.

### 7. Verify

Run the test suite after each batch of removals:

```bash
./gradlew test
```

Check for compile errors on all modified Java files before finishing.
