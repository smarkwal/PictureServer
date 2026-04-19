package net.markwalder.pictureserver.web;

import java.util.Locale;
import java.util.Map;

public final class ImageTypes {

    private static final Map<String, String> MIME_TYPES = Map.of(
            ".jpg", "image/jpeg",
            ".jpeg", "image/jpeg",
            ".png", "image/png",
            ".gif", "image/gif",
            ".webp", "image/webp",
            ".bmp", "image/bmp"
    );

    private ImageTypes() {
    }

    public static boolean isImageFile(String filename) {
        String lower = filename.toLowerCase(Locale.ROOT);
        return MIME_TYPES.keySet().stream().anyMatch(lower::endsWith);
    }

    public static String mimeType(String filename) {
        String lower = filename.toLowerCase(Locale.ROOT);
        return MIME_TYPES.entrySet().stream()
                .filter(e -> lower.endsWith(e.getKey()))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse("application/octet-stream");
    }
}
