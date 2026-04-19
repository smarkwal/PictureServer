---
name: docs-sync
description: 'Review documentation files for consistency with the codebase. Use for: checking README.md, copilot-instructions.md, and other markdown docs for outdated class names, package names, method names, config keys, build commands, source layout, tech stack versions, and missing or incorrect information; also detect duplicated information across docs and suggest deduplication with a canonical source. Reports all inconsistencies and asks for confirmation before fixing.'
argument-hint: 'Optional: scope to a specific doc file (e.g. "README.md only") or a specific topic (e.g. "source layout")'
---

# Docs Sync

## When to Use

- After adding, removing, or renaming source files, packages, or classes
- After changing the build system, dependencies, or Java version
- After adding new configuration keys or changing `settings.properties` structure
- After adding new features or endpoints that should be documented
- Periodically to keep onboarding docs accurate

## Documents to Review

| File                                     | What to Check                                                                                                      |
| ---------------------------------------- | ------------------------------------------------------------------------------------------------------------------ |
| `README.md`                              | Build/run commands, configuration example, project description                                                     |
| `.github/copilot-instructions.md`        | Tech stack versions, source layout tree, key conventions, build commands, testing guidelines, Context7 library IDs |
| `.github/instructions/*.instructions.md` | Any file-scoped instruction files — check applyTo patterns match actual file structure                             |
| `.github/skills/*/SKILL.md`              | Skill descriptions, example commands, file paths referenced inside the skill body                                  |

## Procedure

### 1. Collect ground truth from the codebase

Run these checks to establish facts before comparing to docs:

**Java version and dependencies**
```bash
grep -E "javaVersion|sourceCompatibility|toolchain|junit" build.gradle.kts
```

**Actual source layout**
```bash
find src -name "*.java" | sort
```

**Settings keys** — read `SettingsLoader.java` to confirm the canonical field names and validation rules.

**Gradle tasks used in docs** — verify each command in docs actually exists:
```bash
./gradlew tasks --all 2>/dev/null | grep -E "run|test|clean|jar|fatJar"
```

**`settings.properties` keys** — confirm from `SettingsLoader.java` and the example in the repo root.

### 2. Compare docs to ground truth

For each document, check:

#### README.md
- [ ] Project description matches actual functionality
- [ ] `settings.properties` example uses the correct keys (`path`, `port`, `username`, `password`)
- [ ] Build/run commands exist as real Gradle tasks
- [ ] No references to removed features or old class names

#### copilot-instructions.md
- [ ] **Language/Java version** matches `build.gradle.kts` toolchain
- [ ] **Gradle version** matches `gradle/wrapper/gradle-wrapper.properties`
- [ ] **Source layout tree** lists every actual source file and no phantom ones
- [ ] **Package structure** is correct (`web/ui/` subpackage exists now)
- [ ] **Test files** listed match what exists under `src/test/`
- [ ] **Key conventions** reflect current code patterns (e.g. `final` classes, records, UI component framework)
- [ ] **Build commands** are valid Gradle tasks
- [ ] **Context7 library IDs** — versions match actual dependency versions in `build.gradle.kts`
- [ ] **Cookie name** `PSSESSION` still matches `SessionManager.java`
- [ ] **`resolveSafePath()`** still exists in `PictureServerHandler.java`

### 2.5 Detect duplicated information across docs

Check for duplicated content blocks between docs, especially between `README.md` and `.github/copilot-instructions.md`:

- Setup/configuration examples (`settings.properties` blocks)
- Build/run/test command snippets
- Project overview paragraphs copied verbatim

When duplicates are found:

1. Choose a canonical source (default: `README.md` for user-facing setup and commands).
2. Keep full details only in the canonical file.
3. Replace duplicated blocks in other files with a short reference to the canonical file.
4. Preserve agent-only constraints in `.github/copilot-instructions.md`.

### 3. Report findings

Present a table of all inconsistencies *before* making any changes:

| File                      | Section       | Issue                                        | Suggested fix                             |
| ------------------------- | ------------- | -------------------------------------------- | ----------------------------------------- |
| `copilot-instructions.md` | Source Layout | Missing `web/ui/` subpackage and its classes | Add the 8 new component files to the tree |
| `README.md`               | Run           | Command `./gradlew run` is correct           | ✅ No change needed                       |

Ask for confirmation on each group of changes (e.g. "Fix source layout section?" or "Update all at once?").

For duplication findings, include an explicit dedup proposal:

| Files                                           | Duplicated section      | Canonical source | Suggested dedup                                                             |
| ----------------------------------------------- | ----------------------- | ---------------- | --------------------------------------------------------------------------- |
| `README.md` + `.github/copilot-instructions.md` | `settings.properties` example | `README.md`      | Keep full properties in README, replace in copilot instructions with "See README" |

### 4. Apply confirmed fixes

Use `replace_string_in_file` or `multi_replace_string_in_file` for all confirmed edits. Prefer one batch per document.

### 5. Validate

Re-read the updated sections to confirm the edits landed correctly. No build step is needed since these are documentation-only changes.
