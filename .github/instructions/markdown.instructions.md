---
applyTo: "**/*.md"
---

# Markdown Style

- Use ATX headings (`#`, `##`, `###`) — never Setext-style (underline with `===` or `---`).
- Leave one blank line before and after headings, code blocks, and lists.
- Use fenced code blocks (` ``` `) with a language identifier (e.g. ` ```java `, ` ```bash `, ` ```properties `).
- Use `**bold**` for emphasis and `` `backticks` `` for inline code, file names, and key names.
- Use `-` for unordered list items; do not mix `-`, `*`, and `+` in the same file.
- Wrap lines at 120 characters for prose; do not wrap inside code blocks or tables.
- Add spaces around `|` in table cells; pad cell values with trailing spaces so that all `|` column separators are vertically aligned across every row, making the table readable in plain text as well as rendered form.
- Do not use bare URLs — wrap them in angle brackets or use `[text](url)` link syntax.
- End every file with a single newline.
- Do not use HTML tags unless plain Markdown cannot express the required formatting.
