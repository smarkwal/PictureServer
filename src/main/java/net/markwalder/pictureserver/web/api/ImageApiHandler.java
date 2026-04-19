package net.markwalder.pictureserver.web.api;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;

import com.sun.net.httpserver.HttpExchange;

import net.markwalder.pictureserver.auth.SessionManager;
import net.markwalder.pictureserver.config.Settings;
import net.markwalder.pictureserver.security.PanicMonitor;
import net.markwalder.pictureserver.security.ThreatEvent;
import net.markwalder.pictureserver.web.ImageTypes;
import net.markwalder.pictureserver.web.PathSafety;

final class ImageApiHandler {

    private final Settings settings;
    private final SessionManager sessionManager;
    private final PanicMonitor panicMonitor;

    ImageApiHandler(Settings settings, SessionManager sessionManager, PanicMonitor panicMonitor) {
        this.settings = settings;
        this.sessionManager = sessionManager;
        this.panicMonitor = panicMonitor;
    }

    void handle(HttpExchange exchange, String pathSuffix, String sourceIp, String userAgent) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            JsonHelper.sendJson(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }

        Optional<String> cookie = JsonHelper.readCookie(exchange, sessionManager.cookieName());
        if (cookie.isEmpty() || !sessionManager.isAuthenticated(cookie.get())) {
            JsonHelper.sendJson(exchange, 403, Map.of("error", "Forbidden"));
            return;
        }

        Path imageFsPath;
        try {
            imageFsPath = PathSafety.resolveSafePath(pathSuffix, settings.rootDirectory());
        } catch (SecurityException ex) {
            panicMonitor.recordEvent(ThreatEvent.PATH_TRAVERSAL_ATTEMPT, sourceIp, userAgent);
            JsonHelper.sendJson(exchange, 403, Map.of("error", "Forbidden"));
            return;
        }

        if (!Files.isRegularFile(imageFsPath) || !ImageTypes.isImageFile(imageFsPath.getFileName().toString())) {
            panicMonitor.recordEvent(ThreatEvent.EXCESSIVE_404, sourceIp, userAgent);
            JsonHelper.sendJson(exchange, 404, Map.of("error", "Image not found"));
            return;
        }

        exchange.getResponseHeaders().set("Content-Type", ImageTypes.mimeType(imageFsPath.getFileName().toString()));
        long size = Files.size(imageFsPath);
        exchange.sendResponseHeaders(200, size);
        try (OutputStream out = exchange.getResponseBody();
             InputStream in = Files.newInputStream(imageFsPath)) {
            in.transferTo(out);
        }
    }
}
