package net.markwalder.pictureserver.web;

import java.util.List;

public final class HtmlRenderer {

    public String renderLoginPage(String next, String errorMessage) {
        String errorHtml = errorMessage == null
                ? ""
                : "<p class=\"error\">" + escapeHtml(errorMessage) + "</p>";

        return """
                <!doctype html>
                <html lang=\"en\">
                <head>
                  <meta charset=\"utf-8\">
                  <meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">
                  <title>Picture Server Login</title>
                  <style>
                    body { font-family: Georgia, serif; background: linear-gradient(120deg, #f7efe3, #dce7ef); margin: 0; min-height: 100vh; display: grid; place-items: center; }
                    .card { background: #fff; width: min(420px, 92vw); border-radius: 14px; box-shadow: 0 14px 35px rgba(31,40,51,.18); padding: 24px; }
                    h1 { margin-top: 0; font-size: 1.5rem; }
                    input { width: 100%%; box-sizing: border-box; padding: 10px; margin: 8px 0 14px; border: 1px solid #c8ccd0; border-radius: 8px; }
                    button { width: 100%%; padding: 10px; border: none; border-radius: 8px; background: #1f6357; color: #fff; font-weight: 700; cursor: pointer; }
                    .error { color: #b03030; }
                  </style>
                </head>
                <body>
                  <main class=\"card\">
                    <h1>Picture Server</h1>
                    <p>Please enter your password.</p>
                    %s
                    <form method=\"post\" action=\"/login\">
                      <input type=\"hidden\" name=\"next\" value=\"%s\">
                      <label for=\"password\">Password</label>
                      <input id=\"password\" name=\"password\" type=\"password\" required autofocus>
                      <button type=\"submit\">Sign in</button>
                    </form>
                  </main>
                </body>
                </html>
                """.formatted(errorHtml, escapeHtml(next == null ? "/" : next));
    }

    public String renderAlbumPage(String currentPath, String parentPath, List<String> albums, List<String> pictures) {
        StringBuilder grid = new StringBuilder();
        for (String album : albums) {
            String href = joinPaths(currentPath, album);
            grid.append("<a class=\"tile album\" href=\"")
                    .append(escapeHtml(href))
                    .append("\"><span class=\"icon\">📁</span><span>")
                    .append(escapeHtml(album))
                    .append("</span></a>");
        }

        for (String picture : pictures) {
            String filePath = joinPaths(currentPath, picture);
            String href = filePath + ".html";
            grid.append("<a class=\"tile picture\" href=\"")
                    .append(escapeHtml(href))
                    .append("\"><span class=\"icon\">🖼️</span><span>")
                    .append(escapeHtml(picture))
                    .append("</span></a>");
        }

        String parent = parentPath == null ? "" : "<a class=\"back\" href=\"" + escapeHtml(parentPath) + "\">← Parent album</a>";

        return """
                <!doctype html>
                <html lang=\"en\">
                <head>
                  <meta charset=\"utf-8\">
                  <meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">
                  <title>Album</title>
                  <style>
                    :root { --bg1:#f4f7e8; --bg2:#f2e7dc; --ink:#222; --panel:#ffffffde; --accent:#1d5a7a; }
                    body { margin: 0; font-family: "Palatino Linotype", "Book Antiqua", serif; color: var(--ink); background: radial-gradient(circle at top left, var(--bg1), var(--bg2)); }
                    header { padding: 18px 24px; display:flex; justify-content:space-between; align-items:center; gap:10px; background: #ffffffcc; backdrop-filter: blur(4px); position: sticky; top: 0; }
                    h1 { margin: 0; font-size: 1.1rem; }
                    .links { display:flex; gap: 14px; align-items:center; }
                    a { color: var(--accent); text-decoration: none; }
                    a:hover { text-decoration: underline; }
                    .grid { padding: 20px; display: grid; grid-template-columns: repeat(auto-fill, minmax(170px, 1fr)); gap: 14px; }
                    .tile { background: var(--panel); border: 1px solid #d8d8d8; border-radius: 12px; min-height: 100px; display: flex; flex-direction: column; gap: 10px; align-items: center; justify-content: center; padding: 12px; text-align: center; box-shadow: 0 5px 14px rgba(0,0,0,.08); transition: transform .18s ease, box-shadow .18s ease; }
                    .tile:hover { transform: translateY(-2px); box-shadow: 0 10px 22px rgba(0,0,0,.12); text-decoration: none; }
                    .icon { font-size: 1.6rem; }
                    .empty { padding: 28px; text-align:center; color:#555; }
                  </style>
                </head>
                <body>
                  <header>
                    <h1>Album: %s</h1>
                    <div class=\"links\">%s<a href=\"/logout\">Logout</a></div>
                  </header>
                  %s
                </body>
                </html>
                """.formatted(
                escapeHtml(currentPath.isBlank() ? "/" : currentPath),
                parent,
                grid.length() == 0 ? "<p class=\"empty\">No albums or pictures here.</p>" : "<section class=\"grid\">" + grid + "</section>");
    }

    public String renderPicturePage(String displayPath, String parentPath, String imageSrc) {
        String parent = parentPath == null ? "/" : parentPath;
        return """
                <!doctype html>
                <html lang=\"en\">
                <head>
                  <meta charset=\"utf-8\">
                  <meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">
                  <title>%s</title>
                  <style>
                    body { margin: 0; font-family: Georgia, serif; background: #f2f2f2; }
                    header { padding: 12px 18px; background: #fff; display:flex; justify-content:space-between; border-bottom:1px solid #ddd; }
                    main { padding: 16px; display: grid; place-items: center; }
                    img { max-width: min(96vw, 1400px); max-height: 85vh; border-radius: 10px; box-shadow: 0 10px 30px rgba(0,0,0,.25); background: #fff; }
                    a { color: #135e93; text-decoration: none; }
                    a:hover { text-decoration: underline; }
                  </style>
                </head>
                <body>
                  <header>
                    <a href=\"%s\">← Back to album</a>
                    <a href=\"/logout\">Logout</a>
                  </header>
                  <main>
                    <img src=\"%s\" alt=\"%s\">
                  </main>
                </body>
                </html>
                """.formatted(escapeHtml(displayPath), escapeHtml(parent), escapeHtml(imageSrc), escapeHtml(displayPath));
    }

    public String renderErrorPage(int status, String message) {
        return """
                <!doctype html>
                <html lang=\"en\">
                <head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>Error</title></head>
                <body style=\"font-family: Georgia, serif; background:#fff8f8; padding:24px;\">
                  <h1>Error %d</h1>
                  <p>%s</p>
                  <p><a href=\"/\">Go to root album</a></p>
                </body>
                </html>
                """.formatted(status, escapeHtml(message));
    }

    public static String escapeHtml(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private static String joinPaths(String currentPath, String entry) {
        if (currentPath == null || currentPath.isBlank() || "/".equals(currentPath)) {
            return "/" + entry;
        }
        return currentPath + "/" + entry;
    }
}
