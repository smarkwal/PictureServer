---
name: dead-code-cleanup
description: 'Audit and remove dead code from the codebase. Use for: finding unused Java parameters, fields, methods, imports, CSS classes/rules, HTML attributes; removing leftover symbols after refactors; cleaning up after feature removal. Produces a report first, then removes each item after confirmation.'
argument-hint: 'Optional: scope to a specific file, package, or symbol type (e.g. "CSS only" or "PictureServerHandler.java")'
---

# Dead Code Cleanup

## When to Use

- After a refactor that removed a feature or changed an API
- When the IDE reports "unused parameter", "unused import", or similar hints
- Periodic codebase hygiene passes
- After a UI redesign that may leave orphaned CSS rules

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

Use `grep_search` for exact-text searches and `semantic_search` for broader symbol usage checks.

### 3. Audit CSS

Extract all class selectors from `*.css` resource files:

```bash
grep -oE '\.[a-z][a-z0-9-]+' src/main/resources/*.css | sort -u
```

For each CSS class, check usage across all HTML-producing Java sources (text blocks, HtmlRenderer, UI components):

```bash
grep -r "class-name" src/main/java/
```

Flag any class with zero hits as a candidate for removal.

### 4. Report findings

Present a table of candidates grouped by type before making any changes:

| Type | Location | Symbol | Reason |
|------|----------|--------|--------|
| Unused parameter | `HtmlRenderer.java:45` | `parentPath` | Never read in method body |
| Unused CSS rule | `styles.css:239` | `.nav-spacer` | No HTML element uses this class |

Ask the user to confirm before proceeding with removals.

### 5. Remove confirmed items

- Remove unused parameters from signatures **and** all call sites simultaneously using `multi_replace_string_in_file`.
- Remove unused CSS rules including their full rule block.
- Remove unused imports individually.
- Never remove a symbol that is part of a public API without explicit confirmation.

### 6. Verify

Run the test suite after each batch of removals:

```bash
./gradlew test
```

Check for compile errors with `get_errors` on all modified files before finishing.
