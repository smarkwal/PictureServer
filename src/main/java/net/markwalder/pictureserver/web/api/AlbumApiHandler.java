package net.markwalder.pictureserver.web.api;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.markwalder.pictureserver.security.PanicMonitor;
import net.markwalder.pictureserver.security.ThreatEvent;
import net.markwalder.pictureserver.web.WebPaths;
import net.markwalder.pictureserver.web.service.PictureRepository;
import net.markwalder.pictureserver.web.service.PictureRepository.AlbumInfo;

final class AlbumApiHandler {

    private record AlbumResponse(
            String path,
            List<String> albums,
            Map<String, String> albumPreviews,
            List<String> pictures) {
    }

    private final PictureRepository repository;
    private final PanicMonitor panicMonitor;

    AlbumApiHandler(PictureRepository repository, PanicMonitor panicMonitor) {
        this.repository = repository;
        this.panicMonitor = panicMonitor;
    }

    void handle(HttpExchange exchange, String pathSuffix) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            JsonHelper.sendJson(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }

        String albumWebPath = WebPaths.normalizeWebPath(pathSuffix);

        // Serve virtual Favorites album
        if ("/Favorites".equals(albumWebPath)) {
            Optional<AlbumInfo> favoritesInfo = repository.getFavoritesAlbumInfo();
            AlbumInfo info = favoritesInfo.get();
            JsonHelper.sendJson(exchange, 200, new AlbumResponse("/Favorites", List.of(), Map.of(), info.pictures()));
            return;
        }

        // Fetch album info
        Optional<AlbumInfo> albumInfo;
        try {
            albumInfo = repository.getAlbumInfo(pathSuffix);
        } catch (SecurityException ex) {
            panicMonitor.recordEvent(ThreatEvent.PATH_TRAVERSAL_ATTEMPT, HttpHelper.getSourceIp(exchange), HttpHelper.getUserAgent(exchange));
            JsonHelper.sendJson(exchange, 403, Map.of("error", "Forbidden"));
            return;
        }

        if (albumInfo.isEmpty()) {
            panicMonitor.recordEvent(ThreatEvent.EXCESSIVE_404, HttpHelper.getSourceIp(exchange), HttpHelper.getUserAgent(exchange));
            JsonHelper.sendJson(exchange, 404, Map.of("error", "Album not found"));
            return;
        }

        // Build response
        AlbumInfo info = albumInfo.get();
        String encodedAlbumWebPath = WebPaths.encodeWebPath(albumWebPath);
        String base = "/".equals(albumWebPath) ? "" : albumWebPath;
        Map<String, String> albumPreviews = new LinkedHashMap<>();
        info.albumFirstImages().forEach((album, firstImage) -> {
            String previewPath = base + "/" + album + "/" + firstImage;
            albumPreviews.put(album, "/api/images" + WebPaths.encodeWebPath(previewPath));
        });

        // Prepend virtual Favorites album on home page
        List<String> albums = info.albums();
        if ("/".equals(albumWebPath)) {
            albums = new ArrayList<>(info.albums());
            albums.add(0, "Favorites");
            try {
                Optional<AlbumInfo> favoritesInfo = repository.getFavoritesAlbumInfo();
                List<String> favPictures = favoritesInfo.get().pictures();
                if (!favPictures.isEmpty()) {
                    String previewUrl = "/api/images" + WebPaths.encodeWebPath(favPictures.get(0));
                    albumPreviews.put("Favorites", previewUrl);
                }
            } catch (IOException ignored) {
                // no preview if favorites file is unreadable
            }
        }

        JsonHelper.sendJson(exchange, 200, new AlbumResponse(encodedAlbumWebPath, albums, albumPreviews, info.pictures()));
    }
}
