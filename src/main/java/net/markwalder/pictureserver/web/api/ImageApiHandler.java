package net.markwalder.pictureserver.web.api;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Map;
import java.util.Optional;
import net.markwalder.pictureserver.auth.SessionManager;
import net.markwalder.pictureserver.config.Settings;
import net.markwalder.pictureserver.security.PanicMonitor;
import net.markwalder.pictureserver.security.ThreatEvent;
import net.markwalder.pictureserver.web.CacheHelper;
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

    void handle(HttpExchange exchange, String pathSuffix) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            JsonHelper.sendJson(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }

        Optional<String> cookie = HttpHelper.readCookie(exchange, sessionManager.cookieName());
        if (cookie.isEmpty() || !sessionManager.isAuthenticated(cookie.get(), HttpHelper.getSourceIp(exchange), HttpHelper.getUserAgent(exchange))) {
            JsonHelper.sendJson(exchange, 403, Map.of("error", "Forbidden"));
            return;
        }

        Path imageFsPath;
        try {
            imageFsPath = PathSafety.resolveSafePath(pathSuffix, settings.rootDirectory());
        } catch (SecurityException ex) {
            panicMonitor.recordEvent(ThreatEvent.PATH_TRAVERSAL_ATTEMPT, HttpHelper.getSourceIp(exchange), HttpHelper.getUserAgent(exchange));
            JsonHelper.sendJson(exchange, 403, Map.of("error", "Forbidden"));
            return;
        }

        if (!Files.isRegularFile(imageFsPath) || !ImageTypes.isImageFile(imageFsPath.getFileName().toString())) {
            panicMonitor.recordEvent(ThreatEvent.EXCESSIVE_404, HttpHelper.getSourceIp(exchange), HttpHelper.getUserAgent(exchange));
            JsonHelper.sendJson(exchange, 404, Map.of("error", "Image not found"));
            return;
        }

        long size = Files.size(imageFsPath);
        FileTime lastModified = Files.getLastModifiedTime(imageFsPath);
        String eTag = CacheHelper.buildETag(size, lastModified.toMillis());

        exchange.getResponseHeaders().set("Content-Type", ImageTypes.mimeType(imageFsPath.getFileName().toString()));
        exchange.getResponseHeaders().set("Cache-Control", "private, must-revalidate");
        exchange.getResponseHeaders().set("ETag", eTag);
        exchange.getResponseHeaders().set("Last-Modified", CacheHelper.formatHttpDate(lastModified.toMillis()));

        if (CacheHelper.isNotModified(exchange, eTag, lastModified.toMillis())) {
            exchange.sendResponseHeaders(304, -1);
            return;
        }

        exchange.sendResponseHeaders(200, size);
        try (OutputStream out = exchange.getResponseBody();
             InputStream in = Files.newInputStream(imageFsPath)) {
            in.transferTo(out);
        }
    }

}
