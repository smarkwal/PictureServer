package net.markwalder.pictureserver.web.api;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
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

    private static final DateTimeFormatter HTTP_DATE_FORMATTER = DateTimeFormatter.RFC_1123_DATE_TIME;

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
        String eTag = buildETag(size, lastModified);

        exchange.getResponseHeaders().set("Content-Type", ImageTypes.mimeType(imageFsPath.getFileName().toString()));
        exchange.getResponseHeaders().set("Cache-Control", "private, must-revalidate");
        exchange.getResponseHeaders().set("ETag", eTag);
        exchange.getResponseHeaders().set("Last-Modified", formatHttpDate(lastModified));

        if (isNotModified(exchange, eTag, lastModified)) {
            exchange.sendResponseHeaders(304, -1);
            return;
        }

        exchange.sendResponseHeaders(200, size);
        try (OutputStream out = exchange.getResponseBody();
             InputStream in = Files.newInputStream(imageFsPath)) {
            in.transferTo(out);
        }
    }

    private static String buildETag(long size, FileTime lastModified) {
        return "\"" + Long.toHexString(size) + '-' + Long.toHexString(lastModified.toMillis()) + "\"";
    }

    private static String formatHttpDate(FileTime lastModified) {
        return HTTP_DATE_FORMATTER.format(ZonedDateTime.ofInstant(lastModified.toInstant(), ZoneOffset.UTC));
    }

    private static boolean isNotModified(HttpExchange exchange, String eTag, FileTime lastModified) {
        String ifNoneMatch = exchange.getRequestHeaders().getFirst("If-None-Match");
        if (ifNoneMatch != null) {
            return eTagMatches(ifNoneMatch, eTag);
        }

        String ifModifiedSince = exchange.getRequestHeaders().getFirst("If-Modified-Since");
        if (ifModifiedSince == null) {
            return false;
        }

        try {
            long requestTimeMillis = ZonedDateTime.parse(ifModifiedSince, HTTP_DATE_FORMATTER)
                    .toInstant()
                    .toEpochMilli();
            long fileTimeMillis = (lastModified.toMillis() / 1000L) * 1000L;
            return fileTimeMillis <= requestTimeMillis;
        } catch (DateTimeParseException ex) {
            return false;
        }
    }

    private static boolean eTagMatches(String ifNoneMatch, String currentETag) {
        String normalizedCurrentETag = normalizeETag(currentETag);
        for (String candidate : ifNoneMatch.split(",")) {
            String normalizedCandidate = normalizeETag(candidate.trim());
            if ("*".equals(normalizedCandidate) || normalizedCurrentETag.equals(normalizedCandidate)) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeETag(String eTag) {
        String normalized = eTag.trim();
        if (normalized.startsWith("W/")) {
            return normalized.substring(2).trim();
        }
        return normalized;
    }
}
