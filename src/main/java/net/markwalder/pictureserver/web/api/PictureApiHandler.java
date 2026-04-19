package net.markwalder.pictureserver.web.api;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.sun.net.httpserver.HttpExchange;

import net.markwalder.pictureserver.config.Settings;
import net.markwalder.pictureserver.security.PanicMonitor;
import net.markwalder.pictureserver.security.ThreatEvent;
import net.markwalder.pictureserver.web.ImageTypes;
import net.markwalder.pictureserver.web.PathSafety;
import net.markwalder.pictureserver.web.WebPaths;
import net.markwalder.pictureserver.web.service.PictureService;

final class PictureApiHandler {

    private record PictureResponse(String path, String src, List<String> siblings) {
    }

    private final Settings settings;
    private final PanicMonitor panicMonitor;

    PictureApiHandler(Settings settings, PanicMonitor panicMonitor) {
        this.settings = settings;
        this.panicMonitor = panicMonitor;
    }

    void handleGet(HttpExchange exchange, String pathSuffix) throws IOException {
        String pictureWebPath = WebPaths.normalizeWebPath(pathSuffix);
        String encodedPictureWebPath = WebPaths.encodeWebPath(pictureWebPath);

        Path pictureFsPath;
        try {
            pictureFsPath = PathSafety.resolveSafePath(pathSuffix, settings.rootDirectory());
        } catch (SecurityException ex) {
            panicMonitor.recordEvent(ThreatEvent.PATH_TRAVERSAL_ATTEMPT, HttpHelper.getSourceIp(exchange), HttpHelper.getUserAgent(exchange));
            JsonHelper.sendJson(exchange, 403, Map.of("error", "Forbidden"));
            return;
        }

        if (!Files.isRegularFile(pictureFsPath) || !ImageTypes.isImageFile(pictureFsPath.getFileName().toString())) {
            panicMonitor.recordEvent(ThreatEvent.EXCESSIVE_404, HttpHelper.getSourceIp(exchange), HttpHelper.getUserAgent(exchange));
            JsonHelper.sendJson(exchange, 404, Map.of("error", "Picture not found"));
            return;
        }

        PictureService.PictureInfo info = PictureService.getPictureInfo(pictureFsPath);

        String parentWebPath = WebPaths.parentWebPath(pictureWebPath);
        String parentPrefix = (parentWebPath == null || "/".equals(parentWebPath)) ? "" : parentWebPath;

        List<String> siblings = new ArrayList<>();
        for (String name : info.siblingNames()) {
            siblings.add(WebPaths.encodeWebPath(parentPrefix + "/" + name));
        }

        String src = "/api/images" + encodedPictureWebPath;
        JsonHelper.sendJson(exchange, 200, new PictureResponse(encodedPictureWebPath, src, siblings));
    }

    void handleDelete(HttpExchange exchange, String pathSuffix) throws IOException {
        Path pictureFsPath;
        try {
            pictureFsPath = PathSafety.resolveSafePath(pathSuffix, settings.rootDirectory());
        } catch (SecurityException ex) {
            panicMonitor.recordEvent(ThreatEvent.PATH_TRAVERSAL_ATTEMPT, HttpHelper.getSourceIp(exchange), HttpHelper.getUserAgent(exchange));
            JsonHelper.sendJson(exchange, 403, Map.of("error", "Forbidden"));
            return;
        }

        if (!Files.isRegularFile(pictureFsPath) || !ImageTypes.isImageFile(pictureFsPath.getFileName().toString())) {
            JsonHelper.sendJson(exchange, 404, Map.of("error", "Picture not found"));
            return;
        }

        if (!moveToTrash(pictureFsPath)) {
            JsonHelper.sendJson(exchange, 500, Map.of("error", "Moving to trash is not supported on this system"));
            return;
        }

        JsonHelper.sendJson(exchange, 200, Map.of("success", true));
    }

    private boolean moveToTrash(Path imagePath) {
        if (!Desktop.isDesktopSupported()) {
            return false;
        }
        Desktop desktop = Desktop.getDesktop();
        if (!desktop.isSupported(Desktop.Action.MOVE_TO_TRASH)) {
            return false;
        }
        return desktop.moveToTrash(imagePath.toFile());
    }
}
