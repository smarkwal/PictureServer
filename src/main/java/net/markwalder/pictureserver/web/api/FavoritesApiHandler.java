package net.markwalder.pictureserver.web.api;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import net.markwalder.pictureserver.security.PanicMonitor;
import net.markwalder.pictureserver.security.ThreatEvent;
import net.markwalder.pictureserver.web.service.PictureRepository;

final class FavoritesApiHandler {

    private final PictureRepository repository;
    private final PanicMonitor panicMonitor;

    FavoritesApiHandler(PictureRepository repository, PanicMonitor panicMonitor) {
        this.repository = repository;
        this.panicMonitor = panicMonitor;
    }

    void handleAdd(HttpExchange exchange, String pathSuffix) throws IOException {
        // Strip /Favorites/ prefix if navigating from favorites context
        String realSuffix = stripFavoritesPrefix(pathSuffix);

        // Add to favorites
        Optional<Boolean> result;
        try {
            result = repository.addFavorite(realSuffix);
        } catch (SecurityException ex) {
            panicMonitor.recordEvent(ThreatEvent.PATH_TRAVERSAL_ATTEMPT, HttpHelper.getSourceIp(exchange), HttpHelper.getUserAgent(exchange));
            JsonHelper.sendJson(exchange, 403, Map.of("error", "Forbidden"));
            return;
        }

        if (result.isEmpty()) {
            JsonHelper.sendJson(exchange, 404, Map.of("error", "Picture not found"));
            return;
        }

        JsonHelper.sendJson(exchange, 200, Map.of("favorite", true));
    }

    void handleRemove(HttpExchange exchange, String pathSuffix) throws IOException {
        // Strip /Favorites/ prefix if navigating from favorites context
        String realSuffix = stripFavoritesPrefix(pathSuffix);

        // Remove from favorites
        Optional<Boolean> result;
        try {
            result = repository.removeFavorite(realSuffix);
        } catch (SecurityException ex) {
            panicMonitor.recordEvent(ThreatEvent.PATH_TRAVERSAL_ATTEMPT, HttpHelper.getSourceIp(exchange), HttpHelper.getUserAgent(exchange));
            JsonHelper.sendJson(exchange, 403, Map.of("error", "Forbidden"));
            return;
        }

        if (result.isEmpty()) {
            JsonHelper.sendJson(exchange, 404, Map.of("error", "Picture not found"));
            return;
        }

        JsonHelper.sendJson(exchange, 200, Map.of("favorite", false));
    }

    private static String stripFavoritesPrefix(String pathSuffix) {
        if (pathSuffix.startsWith("/Favorites/")) {
            return pathSuffix.substring("/Favorites".length());
        }
        return pathSuffix;
    }
}
