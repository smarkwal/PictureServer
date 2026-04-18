package net.markwalder.pictureserver.web;

import java.util.List;

import net.markwalder.pictureserver.web.ui.AlbumGridComponent;
import net.markwalder.pictureserver.web.ui.BreadcrumbComponent;
import net.markwalder.pictureserver.web.ui.ConfirmationDialogComponent;
import net.markwalder.pictureserver.web.ui.HtmlEscaper;
import net.markwalder.pictureserver.web.ui.MenuDialogItemComponent;
import net.markwalder.pictureserver.web.ui.MenuLinkItemComponent;
import net.markwalder.pictureserver.web.ui.UiComponent;
import net.markwalder.pictureserver.web.ui.UserMenuComponent;

public final class HtmlRenderer {

    public String renderLoginPage(String next, String errorMessage) {
        String errorHtml = errorMessage == null
                ? ""
          : "<p class=\"error\">" + HtmlEscaper.escape(errorMessage) + "</p>";

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
                """.formatted(errorHtml, HtmlEscaper.escape(next == null ? "/" : next));
    }

    public String renderAlbumPage(String currentPath, String parentPath, List<String> albums, List<String> pictures) {
        String grid = new AlbumGridComponent(currentPath, albums, pictures).render();

        String parent = parentPath == null
            ? "<span class=\"nav-spacer\"></span>"
                : "<a class=\"back\" href=\"" + HtmlEscaper.escape(parentPath) + "\">← Back to parent album</a>";
        String breadcrumb = new BreadcrumbComponent(currentPath).render();
        boolean isHomePage = currentPath == null || currentPath.isBlank() || "/".equals(currentPath);

        List<UiComponent> menuItems = new java.util.ArrayList<>();
        if (isHomePage) {
        menuItems.add(new MenuDialogItemComponent("Shutdown", "shutdown-dialog", true));
        }
        menuItems.add(new MenuLinkItemComponent("Logout", "/logout"));

        String userMenu = new UserMenuComponent(menuItems).render();
        String confirmationDialog = isHomePage
                ? new ConfirmationDialogComponent(
            "shutdown-dialog",
            "Stop the server?",
            "/shutdown-server",
            null,
            null,
            "Shutdown",
            true).render()
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
                grid,
                confirmationDialog);
    }

    public String renderPicturePage(String displayPath, String parentPath, String imageSrc) {
        String parent = parentPath == null ? "/" : parentPath;
        String breadcrumb = new BreadcrumbComponent(displayPath).render();
        List<UiComponent> menuItems = List.of(
                new MenuDialogItemComponent("Delete", "delete-dialog", true),
                new MenuLinkItemComponent("Logout", "/logout"));

        String userMenu = new UserMenuComponent(menuItems).render();
        String confirmationDialog = new ConfirmationDialogComponent(
                "delete-dialog",
                "Delete this image?",
                "/delete-image",
                "p",
                displayPath,
                "Delete",
                true).render();
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
                HtmlEscaper.escape(displayPath),
                HtmlEscaper.escape(parent),
                breadcrumb,
                userMenu,
                HtmlEscaper.escape(imageSrc),
                HtmlEscaper.escape(displayPath),
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
                """.formatted(HtmlEscaper.escape(title), HtmlEscaper.escape(title), HtmlEscaper.escape(message));
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
                """.formatted(status, HtmlEscaper.escape(message));
    }
}
