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
                  <title>Picture Server Login</title>                  <link rel="icon" type="image/svg+xml" href="/icon.svg">                  <link rel=\"stylesheet\" href=\"/styles.css\">
                </head>
                <body class=\"page-login\">
                  <main class=\"card\">                    <img src="/icon.svg" alt="Picture Server" class="app-icon">                    <h1>Picture Server</h1>
                    <p>Please enter your username and password.</p>
                    %s
                    <form method=\"post\" action=\"/login\">
                      <input type=\"hidden\" name=\"next\" value=\"%s\">
                      <label for=\"username\">Username</label>
                      <input id=\"username\" name=\"username\" type=\"text\" required autofocus>
                      <label for=\"password\">Password</label>
                      <input id=\"password\" name=\"password\" type=\"password\" required>
                      <button type=\"submit\">Sign in</button>
                    </form>
                  </main>
                </body>
                </html>
                """.formatted(errorHtml, HtmlEscaper.escape(next == null ? "/" : next));
    }

    public String renderAlbumPage(String albumName, String currentPath, List<String> albums, List<String> pictures) {
        String grid = new AlbumGridComponent(currentPath, albums, pictures).render();

        String breadcrumb = new BreadcrumbComponent(currentPath).render();
        boolean isHomePage = currentPath == null || currentPath.isBlank() || "/".equals(currentPath);
        String title = isHomePage ? "Home" : albumName;

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
                  <title>%s</title>                  <link rel="icon" type="image/svg+xml" href="/icon.svg">                  <link rel=\"stylesheet\" href=\"/styles.css\">
                </head>
                <body class=\"page-album\">
                  <header>
                    <div class="left-nav"><a href="/"><img src="/icon.svg" alt="Home" class="header-icon"></a></div>
                    <p class="crumbs">%s</p>
                    <div class="right-nav">%s</div>
                  </header>
                  %s
                  %s
                </body>
                </html>
                """.formatted(
                HtmlEscaper.escape(title),
                breadcrumb,
                userMenu,
                grid,
                confirmationDialog);
    }

    public String renderPicturePage(String displayPath, String imageSrc, List<String> siblingPictures) {
        String fileName = displayPath.contains("/")
                ? displayPath.substring(displayPath.lastIndexOf('/') + 1)
                : displayPath;
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

        StringBuilder sidebarHtml = new StringBuilder();
        if (!siblingPictures.isEmpty()) {
            sidebarHtml.append("<nav class=\"picture-sidebar\">");
            for (String sibling : siblingPictures) {
                String siblingName = sibling.contains("/") ? sibling.substring(sibling.lastIndexOf('/') + 1) : sibling;
                boolean isCurrent = sibling.equals(displayPath);
                sidebarHtml.append("<a href=\"")
                        .append(HtmlEscaper.escape(sibling + ".html"))
                        .append("\" class=\"sidebar-thumb")
                        .append(isCurrent ? " sidebar-thumb-current" : "")
                        .append("\" title=\"")
                        .append(HtmlEscaper.escape(siblingName))
                        .append("\"><img class=\"sidebar-thumb-image")
                        .append(isCurrent ? " sidebar-thumb-image-current" : "")
                        .append("\" src=\"")
                        .append(HtmlEscaper.escape(sibling))
                        .append("\" alt=\"")
                        .append(HtmlEscaper.escape(siblingName))
                        .append("\"></a>");
            }
            sidebarHtml.append("</nav>");
        }

        return """
                <!doctype html>
                <html lang=\"en\">
                <head>
                  <meta charset=\"utf-8\">
                  <meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">
                  <title>%s</title>                  <link rel="icon" type="image/svg+xml" href="/icon.svg">                  <link rel=\"stylesheet\" href=\"/styles.css\">
                </head>
                <body class=\"page-picture\">
                  <header>
                    <div class="left-nav"><a href="/"><img src="/icon.svg" alt="Home" class="header-icon"></a></div>
                    <p class="crumbs">%s</p>
                    <div class="right-nav">%s</div>
                  </header>
                  <div class="picture-layout">
                    %s
                    <main>
                      <div class="picture-stage">
                        <img src="%s" alt="%s">
                      </div>
                    </main>
                  </div>
                  %s
                  <script>
                    const currentThumbnail = document.querySelector('.sidebar-thumb-image-current');
                    if (currentThumbnail) {
                      currentThumbnail.scrollIntoView({ block: 'center', inline: 'nearest' });
                    }
                  </script>
                </body>
                </html>
                """.formatted(
                HtmlEscaper.escape(fileName),
                breadcrumb,
                userMenu,
                sidebarHtml.toString(),
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
                  <title>%s</title>                  <link rel="icon" type="image/svg+xml" href="/icon.svg">                  <link rel=\"stylesheet\" href=\"/styles.css\">
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
                  <title>Error</title>                  <link rel="icon" type="image/svg+xml" href="/icon.svg">                  <link rel=\"stylesheet\" href=\"/styles.css\">
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
