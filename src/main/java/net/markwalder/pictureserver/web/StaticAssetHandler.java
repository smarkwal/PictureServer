package net.markwalder.pictureserver.web;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

import com.sun.net.httpserver.HttpExchange;

public final class StaticAssetHandler {

    private static final Map<String, String> CONTENT_TYPES = Map.of(
            ".html", "text/html; charset=utf-8",
            ".css", "text/css; charset=utf-8",
            ".js", "application/javascript; charset=utf-8",
            ".svg", "image/svg+xml"
    );

    private StaticAssetHandler() {
    }

    public static void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            sendError(exchange, 405);
            return;
        }
        String path = exchange.getRequestURI().getPath();
        String filename = path.startsWith("/assets/") ? path.substring("/assets/".length()) : "";
        if (filename.isEmpty() || filename.contains("..")) {
            sendIndex(exchange);
            return;
        }
        serveClasspathResource(exchange, "/assets/" + filename, contentType(filename));
    }

    public static void sendIndex(HttpExchange exchange) throws IOException {
        serveClasspathResource(exchange, "/assets/index.html", "text/html; charset=utf-8");
    }

    private static void serveClasspathResource(HttpExchange exchange, String resource, String contentType) throws IOException {
        try (InputStream in = StaticAssetHandler.class.getResourceAsStream(resource)) {
            if (in == null) {
                sendError(exchange, 404);
                return;
            }
            byte[] bytes = in.readAllBytes();
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        }
    }

    private static void sendError(HttpExchange exchange, int status) throws IOException {
        exchange.sendResponseHeaders(status, -1);
    }

    private static String contentType(String filename) {
        for (Map.Entry<String, String> entry : CONTENT_TYPES.entrySet()) {
            if (filename.endsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        return "application/octet-stream";
    }
}
