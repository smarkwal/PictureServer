package net.markwalder.pictureserver.web.api;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.markwalder.pictureserver.config.Settings;
import net.markwalder.pictureserver.security.PanicMonitor;
import net.markwalder.pictureserver.security.ThreatEvent;
import net.markwalder.pictureserver.web.PathSafety;
import net.markwalder.pictureserver.web.WebPaths;
import net.markwalder.pictureserver.web.service.AlbumService;

final class AlbumApiHandler {

    private record AlbumResponse(
            String path,
            List<String> albums,
            Map<String, String> albumPreviews,
            List<String> pictures) {
    }

    private final Settings settings;
    private final PanicMonitor panicMonitor;

    AlbumApiHandler(Settings settings, PanicMonitor panicMonitor) {
        this.settings = settings;
        this.panicMonitor = panicMonitor;
    }

    void handle(HttpExchange exchange, String pathSuffix) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            JsonHelper.sendJson(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }

        String albumWebPath = WebPaths.normalizeWebPath(pathSuffix);

        // Resolve filesystem path
        Path albumFsPath;
        try {
            albumFsPath = PathSafety.resolveSafePath(pathSuffix, settings.rootDirectory());
        } catch (SecurityException ex) {
            panicMonitor.recordEvent(ThreatEvent.PATH_TRAVERSAL_ATTEMPT, HttpHelper.getSourceIp(exchange), HttpHelper.getUserAgent(exchange));
            JsonHelper.sendJson(exchange, 403, Map.of("error", "Forbidden"));
            return;
        }

        // Validate directory exists
        if (!Files.isDirectory(albumFsPath)) {
            panicMonitor.recordEvent(ThreatEvent.EXCESSIVE_404, HttpHelper.getSourceIp(exchange), HttpHelper.getUserAgent(exchange));
            JsonHelper.sendJson(exchange, 404, Map.of("error", "Album not found"));
            return;
        }

        // List album contents
        AlbumService.AlbumInfo info = AlbumService.listAlbum(albumFsPath);

        // Build album preview URLs
        String encodedAlbumWebPath = WebPaths.encodeWebPath(albumWebPath);
        String base = "/".equals(albumWebPath) ? "" : albumWebPath;
        Map<String, String> albumPreviews = new LinkedHashMap<>();
        info.albumFirstImages().forEach((album, firstImage) -> {
            String previewPath = base + "/" + album + "/" + firstImage;
            albumPreviews.put(album, "/api/images" + WebPaths.encodeWebPath(previewPath));
        });

        // Send response
        JsonHelper.sendJson(exchange, 200, new AlbumResponse(encodedAlbumWebPath, info.albums(), albumPreviews, info.pictures()));
    }
}
