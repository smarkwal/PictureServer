package net.markwalder.pictureserver.web;

import java.awt.Desktop;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import net.markwalder.pictureserver.auth.SessionManager;
import net.markwalder.pictureserver.config.Settings;

public final class PictureServerHandler implements HttpHandler {

    private final Settings settings;
    private final SessionManager sessionManager;
    private final HtmlRenderer htmlRenderer;
    private final Runnable shutdownAction;

    public PictureServerHandler(Settings settings, SessionManager sessionManager, HtmlRenderer htmlRenderer, Runnable shutdownAction) {
        this.settings = settings;
        this.sessionManager = sessionManager;
        this.htmlRenderer = htmlRenderer;
        this.shutdownAction = shutdownAction;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();

            if ("/login".equals(path)) {
                handleLogin(exchange, method);
                return;
            }

            if ("/logout".equals(path)) {
                handleLogout(exchange);
                return;
            }

            if ("/styles.css".equals(path)) {
                handleStyles(exchange, method);
                return;
            }

            if ("/icon.svg".equals(path)) {
                handleIcon(exchange, method);
                return;
            }

            if (!isAuthenticated(exchange)) {
                redirect(exchange, "/login?next=" + encodePath(path));
                return;
            }

            if ("/delete-image".equals(path)) {
                handleDeleteImage(exchange, method);
                return;
            }

            if ("/shutdown-server".equals(path)) {
                handleShutdownServer(exchange, method);
                return;
            }

            if (!"GET".equals(method)) {
                sendHtml(exchange, 405, htmlRenderer.renderErrorPage(405, "Method not allowed."));
                return;
            }

            handleGalleryPath(exchange);
        } catch (IllegalArgumentException ex) {
            sendHtml(exchange, 400, htmlRenderer.renderErrorPage(400, ex.getMessage()));
        } catch (SecurityException ex) {
            sendHtml(exchange, 403, htmlRenderer.renderErrorPage(403, ex.getMessage()));
        } catch (IOException | RuntimeException ex) {
            sendHtml(exchange, 500, htmlRenderer.renderErrorPage(500, "Unexpected server error."));
        }
    }

    private void handleLogin(HttpExchange exchange, String method) throws IOException {
        if ("GET".equals(method)) {
            String next = getQueryParams(exchange).getOrDefault("next", "/");
            sendHtml(exchange, 200, htmlRenderer.renderLoginPage(next, null));
            return;
        }

        if (!"POST".equals(method)) {
            sendHtml(exchange, 405, htmlRenderer.renderErrorPage(405, "Method not allowed."));
            return;
        }

        Map<String, String> form = readForm(exchange);
        String password = form.getOrDefault("password", "");
        String next = form.getOrDefault("next", "/");

        if (!settings.password().equals(password)) {
            sendHtml(exchange, 401, htmlRenderer.renderLoginPage(next, "Password does not match settings."));
            return;
        }

        String sessionId = sessionManager.createSession();
        exchange.getResponseHeaders().add("Set-Cookie", sessionManager.cookieName() + "=" + sessionId + "; Path=/; HttpOnly");
        redirect(exchange, sanitizeNext(next));
    }

    private void handleLogout(HttpExchange exchange) throws IOException {
        Optional<String> sessionId = readCookie(exchange, sessionManager.cookieName());
        sessionId.ifPresent(sessionManager::removeSession);
        exchange.getResponseHeaders().add("Set-Cookie", sessionManager.cookieName() + "=; Path=/; HttpOnly; Max-Age=0");
        redirect(exchange, "/login");
    }

    private void handleStyles(HttpExchange exchange, String method) throws IOException {
        if (!"GET".equals(method)) {
            sendHtml(exchange, 405, htmlRenderer.renderErrorPage(405, "Method not allowed."));
            return;
        }

        try (InputStream input = PictureServerHandler.class.getResourceAsStream("/styles.css")) {
            if (input == null) {
                sendHtml(exchange, 500, htmlRenderer.renderErrorPage(500, "Missing stylesheet resource."));
                return;
            }

            byte[] bytes = input.readAllBytes();
            exchange.getResponseHeaders().set("Content-Type", "text/css; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        }
    }

    private void handleIcon(HttpExchange exchange, String method) throws IOException {
        if (!"GET".equals(method)) {
            sendHtml(exchange, 405, htmlRenderer.renderErrorPage(405, "Method not allowed."));
            return;
        }

        try (InputStream input = PictureServerHandler.class.getResourceAsStream("/icon.svg")) {
            if (input == null) {
                sendHtml(exchange, 500, htmlRenderer.renderErrorPage(500, "Missing icon resource."));
                return;
            }

            byte[] bytes = input.readAllBytes();
            exchange.getResponseHeaders().set("Content-Type", "image/svg+xml");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(bytes);
            }
        }
    }

    private void handleGalleryPath(HttpExchange exchange) throws IOException {
        String requestPath = decodePath(exchange.getRequestURI().getPath());

        if (requestPath.endsWith(".html")) {
            handlePicturePage(exchange, requestPath);
            return;
        }

        Path fsPath = resolveSafePath(requestPath);

        if (!Files.exists(fsPath)) {
            sendHtml(exchange, 404, htmlRenderer.renderErrorPage(404, "Path not found."));
            return;
        }

        if (Files.isRegularFile(fsPath) && isImageFile(fsPath.getFileName().toString())) {
            sendImage(exchange, fsPath);
            return;
        }

        if (!Files.isDirectory(fsPath)) {
            sendHtml(exchange, 404, htmlRenderer.renderErrorPage(404, "Not an album directory."));
            return;
        }

        List<String> albums = new ArrayList<>();
        List<String> pictures = new ArrayList<>();

        try (Stream<Path> list = Files.list(fsPath)) {
            for (Path child : list.sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT))).toList()) {
                String name = child.getFileName().toString();
                if (Files.isDirectory(child)) {
                    albums.add(name);
                } else if (Files.isRegularFile(child) && isImageFile(name)) {
                    pictures.add(name);
                }
            }
        }

        String normalizedRequestPath = normalizeWebPath(requestPath);
        String albumName = fsPath.getFileName() == null ? "Album" : fsPath.getFileName().toString();
        String html = htmlRenderer.renderAlbumPage(albumName, normalizedRequestPath, albums, pictures);
        sendHtml(exchange, 200, html);
    }

    private void handleDeleteImage(HttpExchange exchange, String method) throws IOException {
        if (!"POST".equals(method)) {
            sendHtml(exchange, 405, htmlRenderer.renderErrorPage(405, "Method not allowed."));
            return;
        }

        Map<String, String> form = readForm(exchange);
        String requested = form.get("p");
        if (requested == null || requested.isBlank()) {
            throw new IllegalArgumentException("Missing image path.");
        }

        Path imagePath = resolveSafePath(requested);
        if (!Files.isRegularFile(imagePath) || !isImageFile(imagePath.getFileName().toString())) {
            sendHtml(exchange, 404, htmlRenderer.renderErrorPage(404, "Picture not found."));
            return;
        }

        boolean moved = moveToTrash(imagePath);
        if (!moved) {
            sendHtml(exchange, 500, htmlRenderer.renderErrorPage(500, "Moving to trash is not supported on this system."));
            return;
        }

        String normalizedImagePath = normalizeWebPath(requested);
        String parentPath = parentWebPath(normalizedImagePath);
        redirect(exchange, parentPath == null ? "/" : parentPath);
    }

    private void handleShutdownServer(HttpExchange exchange, String method) throws IOException {
        if (!"POST".equals(method)) {
            sendHtml(exchange, 405, htmlRenderer.renderErrorPage(405, "Method not allowed."));
            return;
        }

        sendHtml(exchange, 200, htmlRenderer.renderInfoPage("Shutting down", "The server is shutting down."));
        new Thread(shutdownAction, "shutdown-server-thread").start();
    }

    private void handlePicturePage(HttpExchange exchange, String htmlPath) throws IOException {
        String imageRequestPath = htmlPath.substring(0, htmlPath.length() - ".html".length());
        if (imageRequestPath.isBlank()) {
            sendHtml(exchange, 404, htmlRenderer.renderErrorPage(404, "Picture not found."));
            return;
        }

        Path imagePath = resolveSafePath(imageRequestPath);
        if (!Files.isRegularFile(imagePath) || !isImageFile(imagePath.getFileName().toString())) {
            sendHtml(exchange, 404, htmlRenderer.renderErrorPage(404, "Picture not found."));
            return;
        }

        String normalizedImagePath = normalizeWebPath(imageRequestPath);
        sendHtml(exchange, 200, htmlRenderer.renderPicturePage(normalizedImagePath, normalizedImagePath));
    }

    private void sendImage(HttpExchange exchange, Path imagePath) throws IOException {
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", mimeType(imagePath.getFileName().toString()));
        long size = Files.size(imagePath);
        exchange.sendResponseHeaders(200, size);
        try (OutputStream output = exchange.getResponseBody(); InputStream input = Files.newInputStream(imagePath)) {
            input.transferTo(output);
        }
    }

    private boolean isAuthenticated(HttpExchange exchange) {
        Optional<String> cookie = readCookie(exchange, sessionManager.cookieName());
        return cookie.isPresent() && sessionManager.isAuthenticated(cookie.get());
    }

    private Optional<String> readCookie(HttpExchange exchange, String key) {
        List<String> cookieHeaders = exchange.getRequestHeaders().getOrDefault("Cookie", List.of());
        for (String header : cookieHeaders) {
            String[] parts = header.split(";");
            for (String part : parts) {
                String[] pair = part.trim().split("=", 2);
                if (pair.length == 2 && key.equals(pair[0])) {
                    return Optional.of(pair[1]);
                }
            }
        }
        return Optional.empty();
    }

    private void sendHtml(HttpExchange exchange, int status, String html) throws IOException {
        byte[] body = html.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(body);
        }
    }

    private void redirect(HttpExchange exchange, String target) throws IOException {
        exchange.getResponseHeaders().set("Location", target);
        exchange.sendResponseHeaders(303, -1);
    }

    private Map<String, String> readForm(HttpExchange exchange) throws IOException {
        byte[] bytes = exchange.getRequestBody().readAllBytes();
        String body = new String(bytes, StandardCharsets.UTF_8);
        return parseQuery(body);
    }

    private Map<String, String> getQueryParams(HttpExchange exchange) {
        String rawQuery = exchange.getRequestURI().getRawQuery();
        if (rawQuery == null || rawQuery.isBlank()) {
            return Map.of();
        }
        return parseQuery(rawQuery);
    }

    private Map<String, String> parseQuery(String value) {
        return Stream.of(value.split("&"))
                .filter(part -> !part.isBlank())
                .map(part -> part.split("=", 2))
                .collect(Collectors.toMap(
                        pair -> urlDecode(pair[0]),
                        pair -> pair.length > 1 ? urlDecode(pair[1]) : "",
                        (left, right) -> right,
                        HashMap::new));
    }

    private Path resolveSafePath(String requestPath) {
        String decoded = decodePath(requestPath);
        String trimmed = decoded.startsWith("/") ? decoded.substring(1) : decoded;
        Path relative = Path.of(trimmed).normalize();

        if (relative.isAbsolute() || relative.startsWith("..")) {
            throw new SecurityException("Path traversal attempt blocked.");
        }

        Path root = settings.rootDirectory();
        Path candidate = root.resolve(relative).normalize();

        if (!candidate.startsWith(root)) {
            throw new SecurityException("Path traversal attempt blocked.");
        }

        return candidate;
    }

    private String sanitizeNext(String next) {
        String decoded = decodePath(next);
        if (!decoded.startsWith("/")) {
            return "/";
        }
        return decoded;
    }

    private String decodePath(String path) {
        return urlDecode(path == null ? "" : path);
    }

    private String encodePath(String path) {
        return java.net.URLEncoder.encode(path, StandardCharsets.UTF_8);
    }

    private String urlDecode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private String normalizeWebPath(String path) {
        String normalized = decodePath(path);
        if (normalized.isBlank() || "/".equals(normalized)) {
            return "/";
        }

        String[] parts = normalized.split("/");
        List<String> cleaned = new ArrayList<>();
        for (String part : parts) {
            if (!part.isBlank()) {
                cleaned.add(part);
            }
        }

        if (cleaned.isEmpty()) {
            return "/";
        }

        return "/" + String.join("/", cleaned);
    }

    private String parentWebPath(String currentPath) {
        if (currentPath == null || currentPath.isBlank() || "/".equals(currentPath)) {
            return null;
        }

        int index = currentPath.lastIndexOf('/');
        if (index <= 0) {
            return "/";
        }
        return currentPath.substring(0, index);
    }

    private boolean isImageFile(String filename) {
        String lower = filename.toLowerCase(Locale.ROOT);
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png")
                || lower.endsWith(".gif") || lower.endsWith(".webp") || lower.endsWith(".bmp");
    }

    private String mimeType(String filename) {
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".gif")) {
            return "image/gif";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        if (lower.endsWith(".bmp")) {
            return "image/bmp";
        }
        return "image/jpeg";
    }

    private boolean moveToTrash(Path imagePath) {
        if (!Desktop.isDesktopSupported()) {
            return false;
        }

        Desktop desktop = Desktop.getDesktop();
        if (!desktop.isSupported(Desktop.Action.MOVE_TO_TRASH)) {
            return false;
        }

        return desktop.moveToTrash(imagePath.toFile());
    }
}
