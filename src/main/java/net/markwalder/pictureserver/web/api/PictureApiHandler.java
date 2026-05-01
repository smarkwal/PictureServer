package net.markwalder.pictureserver.web.api;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.markwalder.pictureserver.security.PanicMonitor;
import net.markwalder.pictureserver.security.ThreatEvent;
import net.markwalder.pictureserver.web.WebPaths;
import net.markwalder.pictureserver.web.service.PictureRepository;
import net.markwalder.pictureserver.web.service.PictureRepository.AlbumInfo;
import net.markwalder.pictureserver.web.service.PictureRepository.PictureInfo;

final class PictureApiHandler {

    private record PictureResponse(String path, String name, String src, List<String> siblings, boolean favorite, String realPath) {
    }

    private final PictureRepository repository;
    private final PanicMonitor panicMonitor;

    PictureApiHandler(PictureRepository repository, PanicMonitor panicMonitor) {
        this.repository = repository;
        this.panicMonitor = panicMonitor;
    }

    void handleGet(HttpExchange exchange, String pathSuffix) throws IOException {
        // Handle Favorites-context pictures
        if (pathSuffix.startsWith("/Favorites/")) {
            handleGetFavoritesPicture(exchange, pathSuffix);
            return;
        }

        String pictureWebPath = WebPaths.normalizeWebPath(pathSuffix);
        String encodedPictureWebPath = WebPaths.encodeWebPath(pictureWebPath);

        // Fetch picture info
        Optional<PictureInfo> pictureInfo;
        try {
            pictureInfo = repository.getPictureInfo(pathSuffix);
        } catch (SecurityException ex) {
            panicMonitor.recordEvent(ThreatEvent.PATH_TRAVERSAL_ATTEMPT, HttpHelper.getSourceIp(exchange), HttpHelper.getUserAgent(exchange));
            JsonHelper.sendJson(exchange, 403, Map.of("error", "Forbidden"));
            return;
        }

        if (pictureInfo.isEmpty()) {
            panicMonitor.recordEvent(ThreatEvent.EXCESSIVE_404, HttpHelper.getSourceIp(exchange), HttpHelper.getUserAgent(exchange));
            JsonHelper.sendJson(exchange, 404, Map.of("error", "Picture not found"));
            return;
        }

        // Build response
        PictureInfo info = pictureInfo.get();
        String parentWebPath = WebPaths.parentWebPath(pictureWebPath);
        String parentPrefix = (parentWebPath == null || "/".equals(parentWebPath)) ? "" : parentWebPath;

        List<String> siblings = new ArrayList<>();
        for (String name : info.siblingNames()) {
            siblings.add(WebPaths.encodeWebPath(parentPrefix + "/" + name));
        }

        boolean favorite = repository.isFavorite(pathSuffix).orElse(false);
        String src = "/api/images" + encodedPictureWebPath;
        String pictureName = pictureWebPath.substring(pictureWebPath.lastIndexOf('/') + 1);
        JsonHelper.sendJson(exchange, 200, new PictureResponse(encodedPictureWebPath, pictureName, src, siblings, favorite, encodedPictureWebPath));
    }

    private void handleGetFavoritesPicture(HttpExchange exchange, String pathSuffix) throws IOException {
        // Extract real path (strip "/Favorites" prefix)
        String realSuffix = pathSuffix.substring("/Favorites".length());
        String realWebPath = WebPaths.normalizeWebPath(realSuffix);
        String encodedRealWebPath = WebPaths.encodeWebPath(realWebPath);

        // Validate the file exists
        Optional<PictureInfo> pictureInfo;
        try {
            pictureInfo = repository.getPictureInfo(realSuffix);
        } catch (SecurityException ex) {
            panicMonitor.recordEvent(ThreatEvent.PATH_TRAVERSAL_ATTEMPT, HttpHelper.getSourceIp(exchange), HttpHelper.getUserAgent(exchange));
            JsonHelper.sendJson(exchange, 403, Map.of("error", "Forbidden"));
            return;
        }

        if (pictureInfo.isEmpty()) {
            panicMonitor.recordEvent(ThreatEvent.EXCESSIVE_404, HttpHelper.getSourceIp(exchange), HttpHelper.getUserAgent(exchange));
            JsonHelper.sendJson(exchange, 404, Map.of("error", "Picture not found"));
            return;
        }

        // Build sibling list from all favorites
        Optional<AlbumInfo> favoritesInfo = repository.getFavoritesAlbumInfo();
        List<String> siblings = new ArrayList<>();
        for (String favEntry : favoritesInfo.get().pictures()) {
            siblings.add("/Favorites" + WebPaths.encodeWebPath(favEntry));
        }

        // Build response
        String encodedFavoritesPath = "/Favorites" + encodedRealWebPath;
        String src = "/api/images/Favorites" + encodedRealWebPath;
        String pictureName = realWebPath.substring(realWebPath.lastIndexOf('/') + 1);
        JsonHelper.sendJson(exchange, 200, new PictureResponse(encodedFavoritesPath, pictureName, src, siblings, true, encodedRealWebPath));
    }

    void handleDelete(HttpExchange exchange, String pathSuffix) throws IOException {
        // Move to trash
        Optional<Boolean> result;
        try {
            result = repository.moveToTrash(pathSuffix);
        } catch (SecurityException ex) {
            panicMonitor.recordEvent(ThreatEvent.PATH_TRAVERSAL_ATTEMPT, HttpHelper.getSourceIp(exchange), HttpHelper.getUserAgent(exchange));
            JsonHelper.sendJson(exchange, 403, Map.of("error", "Forbidden"));
            return;
        }

        if (result.isEmpty()) {
            JsonHelper.sendJson(exchange, 404, Map.of("error", "Picture not found"));
            return;
        }

        if (!result.get()) {
            JsonHelper.sendJson(exchange, 500, Map.of("error", "Moving to trash is not supported on this system"));
            return;
        }

        JsonHelper.sendJson(exchange, 200, Map.of("success", true));
    }
}
