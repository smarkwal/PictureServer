package net.markwalder.pictureserver.web.api;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import java.util.Optional;
import net.markwalder.pictureserver.auth.SessionManager;
import net.markwalder.pictureserver.security.PanicMonitor;
import net.markwalder.pictureserver.security.ThreatEvent;
import net.markwalder.pictureserver.web.CacheHelper;
import net.markwalder.pictureserver.web.ImageTypes;
import net.markwalder.pictureserver.web.service.PictureRepository;
import net.markwalder.pictureserver.web.service.PictureRepository.ImageInfo;

final class ImageApiHandler {

    private final PictureRepository repository;
    private final SessionManager sessionManager;
    private final PanicMonitor panicMonitor;

    ImageApiHandler(PictureRepository repository, SessionManager sessionManager, PanicMonitor panicMonitor) {
        this.repository = repository;
        this.sessionManager = sessionManager;
        this.panicMonitor = panicMonitor;
    }

    void handle(HttpExchange exchange, String pathSuffix) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            JsonHelper.sendJson(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }

        // Strip /Favorites/ prefix — serve the real file
        if (pathSuffix.startsWith("/Favorites/")) {
            pathSuffix = pathSuffix.substring("/Favorites".length());
        }

        // Verify authentication
        Optional<String> cookie = HttpHelper.readCookie(exchange, sessionManager.cookieName());
        if (cookie.isEmpty() || !sessionManager.isAuthenticated(cookie.get(), HttpHelper.getSourceIp(exchange), HttpHelper.getUserAgent(exchange))) {
            JsonHelper.sendJson(exchange, 403, Map.of("error", "Forbidden"));
            return;
        }

        // Fetch image metadata
        Optional<ImageInfo> imageInfo;
        try {
            imageInfo = repository.getImageInfo(pathSuffix);
        } catch (SecurityException ex) {
            panicMonitor.recordEvent(ThreatEvent.PATH_TRAVERSAL_ATTEMPT, HttpHelper.getSourceIp(exchange), HttpHelper.getUserAgent(exchange));
            JsonHelper.sendJson(exchange, 403, Map.of("error", "Forbidden"));
            return;
        }

        if (imageInfo.isEmpty()) {
            panicMonitor.recordEvent(ThreatEvent.EXCESSIVE_404, HttpHelper.getSourceIp(exchange), HttpHelper.getUserAgent(exchange));
            JsonHelper.sendJson(exchange, 404, Map.of("error", "Image not found"));
            return;
        }

        // Set cache headers
        ImageInfo info = imageInfo.get();
        String eTag = CacheHelper.buildETag(info.size(), info.lastModifiedMillis());

        exchange.getResponseHeaders().set("Content-Type", ImageTypes.mimeType(info.filename()));
        exchange.getResponseHeaders().set("Cache-Control", "private, max-age=2592000");
        exchange.getResponseHeaders().set("ETag", eTag);
        exchange.getResponseHeaders().set("Last-Modified", CacheHelper.formatHttpDate(info.lastModifiedMillis()));

        // Return 304 if not modified
        if (CacheHelper.isNotModified(exchange, eTag, info.lastModifiedMillis())) {
            exchange.sendResponseHeaders(304, -1);
            return;
        }

        // Stream image to response
        Optional<InputStream> streamOpt = repository.openImage(pathSuffix);
        if (streamOpt.isEmpty()) {
            JsonHelper.sendJson(exchange, 404, Map.of("error", "Image not found"));
            return;
        }
        exchange.sendResponseHeaders(200, info.size());
        try (OutputStream out = exchange.getResponseBody();
             InputStream in = streamOpt.get()) {
            in.transferTo(out);
        }
    }
}
