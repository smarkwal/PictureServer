package net.markwalder.pictureserver.web.api;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.sun.net.httpserver.HttpExchange;

import net.markwalder.pictureserver.config.Settings;
import net.markwalder.pictureserver.security.PanicMonitor;
import net.markwalder.pictureserver.security.ThreatEvent;
import net.markwalder.pictureserver.web.PathSafety;
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

    void handle(HttpExchange exchange, String pathSuffix, String sourceIp, String userAgent) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            JsonHelper.sendJson(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }

        String albumWebPath = PathSafety.normalizeWebPath(pathSuffix);

        Path albumFsPath;
        try {
            albumFsPath = PathSafety.resolveSafePath(pathSuffix, settings.rootDirectory());
        } catch (SecurityException ex) {
            panicMonitor.recordEvent(ThreatEvent.PATH_TRAVERSAL_ATTEMPT, sourceIp, userAgent);
            JsonHelper.sendJson(exchange, 403, Map.of("error", "Forbidden"));
            return;
        }

        if (!Files.isDirectory(albumFsPath)) {
            panicMonitor.recordEvent(ThreatEvent.EXCESSIVE_404, sourceIp, userAgent);
            JsonHelper.sendJson(exchange, 404, Map.of("error", "Album not found"));
            return;
        }

        AlbumService.AlbumInfo info = AlbumService.listAlbum(albumFsPath);

        String base = "/".equals(albumWebPath) ? "" : albumWebPath;
        Map<String, String> albumPreviews = new LinkedHashMap<>();
        info.albumFirstImages().forEach((album, firstImage) ->
                albumPreviews.put(album, "/api/images" + base + "/" + album + "/" + firstImage));

        JsonHelper.sendJson(exchange, 200, new AlbumResponse(albumWebPath, info.albums(), albumPreviews, info.pictures()));
    }
}
