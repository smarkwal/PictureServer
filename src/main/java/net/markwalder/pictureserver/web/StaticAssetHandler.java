package net.markwalder.pictureserver.web;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.Map;

public final class StaticAssetHandler {

    private static final Map<String, String> CONTENT_TYPES = Map.of(
            ".html", "text/html; charset=utf-8",
            ".css", "text/css; charset=utf-8",
            ".js", "application/javascript; charset=utf-8",
            ".svg", "image/svg+xml"
    );

    public StaticAssetHandler() {
    }

    public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendError(exchange, 405);
            return;
        }

        // Extract filename from path
        String path = exchange.getRequestURI().getPath();
        String filename = path.startsWith("/assets/") ? path.substring("/assets/".length()) : "";
        if (filename.isEmpty() || filename.contains("..")) {
            sendIndex(exchange);
            return;
        }

        // Serve asset from classpath
        serveClasspathResource(exchange, "/assets/" + filename, contentType(filename));
    }

    public void sendIndex(HttpExchange exchange) throws IOException {
        serveClasspathResource(exchange, "/assets/index.html", "text/html; charset=utf-8");
    }

    private void serveClasspathResource(HttpExchange exchange, String resource, String contentType) throws IOException {
        // Locate classpath resource
        URL url = StaticAssetHandler.class.getResource(resource);
        if (url == null) {
            sendError(exchange, 404);
            return;
        }

        // Load resource bytes
        URLConnection conn = url.openConnection();
        long lastModifiedMillis = conn.getLastModified();
        byte[] bytes;
        try (InputStream in = conn.getInputStream()) {
            bytes = in.readAllBytes();
        }

        // Set caching headers
        String eTag = CacheHelper.buildETag(bytes.length, lastModifiedMillis);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "public, no-cache");
        exchange.getResponseHeaders().set("ETag", eTag);
        exchange.getResponseHeaders().set("Last-Modified", CacheHelper.formatHttpDate(lastModifiedMillis));

        // Return 304 if not modified
        if (CacheHelper.isNotModified(exchange, eTag, lastModifiedMillis)) {
            exchange.sendResponseHeaders(304, -1);
            return;
        }

        // Send response body
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private void sendError(HttpExchange exchange, int status) throws IOException {
        exchange.sendResponseHeaders(status, -1);
    }

    private String contentType(String filename) {
        for (Map.Entry<String, String> entry : CONTENT_TYPES.entrySet()) {
            if (filename.endsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        return "application/octet-stream";
    }
}
