---
applyTo: "**/*.properties"
---

# Java Properties File Style

- Use `=` as the key-value separator, with surrounding spaces (e.g. `key = value`).
- Use lowercase, dot-separated keys in hierarchical order (e.g. `server.port = 8088`).
- Group related keys together; separate groups with a single blank line.
- Add a `#` comment above each key or group to explain its purpose when not self-evident.
- Do not quote values — the `java.util.Properties` parser treats quotes as literal characters.
- Use forward slashes for path values, even on Windows.
- Keep one property per line; do not split values across multiple lines unless necessary.
- Do not include trailing whitespace after values.
