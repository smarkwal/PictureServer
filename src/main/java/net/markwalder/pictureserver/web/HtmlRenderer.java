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
                  <link rel=\"stylesheet\" href=\"/styles.css\">
                </head>
                <body class=\"page-login\">
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
            String rawImageSrc = filePath;
            grid.append("<a class=\"tile picture\" href=\"")
                    .append(escapeHtml(href))
              .append("\"><div class=\"picture-thumb-box\"><img class=\"picture-thumb\" src=\"")
              .append(escapeHtml(rawImageSrc))
              .append("\" alt=\"")
              .append(escapeHtml(picture))
              .append("\"></div></a>");
        }

        String parent = parentPath == null
          ? "<span class=\"nav-spacer\"></span>"
          : "<a class=\"back\" href=\"" + escapeHtml(parentPath) + "\">← Back to parent album</a>";
        String breadcrumb = breadcrumb(currentPath);
        boolean isHomePage = currentPath == null || currentPath.isBlank() || "/".equals(currentPath);

        StringBuilder menuItems = new StringBuilder();
        if (isHomePage) {
            menuItems.append(menuDialogItem("Shutdown", "shutdown-dialog", true));
        }
        menuItems.append(menuLinkItem("Logout", "/logout"));

        String userMenu = userMenuHtml(menuItems.toString());
        String confirmationDialog = isHomePage
          ? confirmationDialogHtml("shutdown-dialog", "Stop the server?", "/shutdown-server", null, null, "Shutdown", true)
          : "";

        return """
                <!doctype html>
                <html lang=\"en\">
                <head>
                  <meta charset=\"utf-8\">
                  <meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">
                  <title>Album</title>
                  <link rel=\"stylesheet\" href=\"/styles.css\">
                </head>
                <body class=\"page-album\">
                  <header>
                    <div class=\"left-nav\">%s</div>
                    <p class=\"crumbs\">%s</p>
                    <div class=\"right-nav\">%s</div>
                  </header>
                  %s
                  %s
                </body>
                </html>
                """.formatted(
                parent,
                breadcrumb,
                userMenu,
                grid.length() == 0 ? "<p class=\"empty\">No albums or pictures here.</p>" : "<section class=\"grid\">" + grid + "</section>",
                confirmationDialog);
    }

    public String renderPicturePage(String displayPath, String parentPath, String imageSrc) {
        String parent = parentPath == null ? "/" : parentPath;
        String breadcrumb = breadcrumb(displayPath);
        StringBuilder menuItems = new StringBuilder();
        menuItems.append(menuDialogItem("Delete", "delete-dialog", true));
        menuItems.append(menuLinkItem("Logout", "/logout"));

        String userMenu = userMenuHtml(menuItems.toString());
        String confirmationDialog = confirmationDialogHtml(
                "delete-dialog",
                "Delete this image?",
                "/delete-image",
                "p",
                displayPath,
                "Delete",
                true);
        return """
                <!doctype html>
                <html lang=\"en\">
                <head>
                  <meta charset=\"utf-8\">
                  <meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">
                  <title>%s</title>
                  <link rel=\"stylesheet\" href=\"/styles.css\">
                </head>
                <body class=\"page-picture\">
                  <header>
                    <div class=\"left-nav\"><a href=\"%s\">← Back to album</a></div>
                    <p class=\"crumbs\">%s</p>
                    <div class=\"right-nav\">%s</div>
                  </header>
                  <main>
                    <img src=\"%s\" alt=\"%s\">
                  </main>
                  %s
                </body>
                </html>
                """.formatted(
                escapeHtml(displayPath),
                escapeHtml(parent),
                breadcrumb,
                userMenu,
                escapeHtml(imageSrc),
                escapeHtml(displayPath),
                confirmationDialog);
    }

    public String renderInfoPage(String title, String message) {
        return """
                <!doctype html>
                <html lang=\"en\">
                <head>
                  <meta charset=\"utf-8\">
                  <meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">
                  <title>%s</title>
                  <link rel=\"stylesheet\" href=\"/styles.css\">
                </head>
                <body class=\"page-error\">
                  <h1>%s</h1>
                  <p>%s</p>
                </body>
                </html>
                """.formatted(escapeHtml(title), escapeHtml(title), escapeHtml(message));
    }

    public String renderErrorPage(int status, String message) {
        return """
                <!doctype html>
                <html lang=\"en\">
                <head>
                  <meta charset=\"utf-8\">
                  <meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">
                  <title>Error</title>
                  <link rel=\"stylesheet\" href=\"/styles.css\">
                </head>
                <body class=\"page-error\">
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

    private static String breadcrumb(String currentPath) {
      String normalized = (currentPath == null || currentPath.isBlank()) ? "/" : currentPath;

      String[] parts = normalized.split("/");
      List<String> cleanParts = new java.util.ArrayList<>();
      for (String part : parts) {
        if (!part.isBlank()) {
          cleanParts.add(part);
        }
      }

      StringBuilder out = new StringBuilder();

      if (cleanParts.isEmpty()) {
        out.append("<span class=\"current\">Home</span>");
        return out.toString();
      }

      out.append("<a href=\"/\">Home</a>");

      StringBuilder runningPath = new StringBuilder();
      for (int i = 0; i < cleanParts.size(); i++) {
        String part = cleanParts.get(i);
        runningPath.append("/").append(part);
        out.append(" / ");
        if (i == cleanParts.size() - 1) {
          out.append("<span class=\"current\">").append(escapeHtml(part)).append("</span>");
        } else {
          out.append("<a href=\"")
                  .append(escapeHtml(runningPath.toString()))
                  .append("\">")
                  .append(escapeHtml(part))
                  .append("</a>");
        }
      }

      return out.toString();
    }

    private static String userMenuHtml(String menuItemsHtml) {
        return """
                <details class=\"user-menu\">
                  <summary class=\"menu-button\" aria-label=\"Open user menu\">☰</summary>
                  <nav class=\"menu-panel\">
                    %s
                  </nav>
                </details>
                """.formatted(menuItemsHtml);
    }

    private static String menuLinkItem(String label, String href) {
        return """
                <a class=\"menu-item\" href=\"%s\" onclick=\"this.closest('details').removeAttribute('open')\">%s</a>
                """.formatted(escapeHtml(href), escapeHtml(label));
    }

    private static String menuDialogItem(String label, String dialogId, boolean danger) {
        return """
                <button type=\"button\" class=\"menu-item %s\" onclick=\"this.closest('details').removeAttribute('open'); document.getElementById('%s').showModal()\">%s</button>
                """.formatted(danger ? "danger" : "", escapeHtml(dialogId), escapeHtml(label));
    }

    private static String confirmationDialogHtml(
            String dialogId,
            String message,
            String action,
            String hiddenName,
            String hiddenValue,
            String confirmLabel,
            boolean danger) {
        String hiddenInput = (hiddenName == null || hiddenName.isBlank())
                ? ""
                : "<input type=\"hidden\" name=\"" + escapeHtml(hiddenName) + "\" value=\"" + escapeHtml(hiddenValue == null ? "" : hiddenValue) + "\">";

        return """
                <dialog id=\"%s\" class=\"confirm-dialog\">
                  <form method=\"post\" action=\"%s\" class=\"confirm-form\">
                    <p>%s</p>
                    %s
                    <div class=\"confirm-actions\">
                      <button type=\"submit\" class=\"%s\">%s</button>
                      <button type=\"submit\" formmethod=\"dialog\">Cancel</button>
                    </div>
                  </form>
                </dialog>
                """.formatted(
                escapeHtml(dialogId),
                escapeHtml(action),
                escapeHtml(message),
                hiddenInput,
                danger ? "danger" : "",
                escapeHtml(confirmLabel));
    }
}
