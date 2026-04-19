package net.markwalder.pictureserver.web.ui;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public final class UrlEncoder {

    private UrlEncoder() {
    }

    public static String encodePath(String path) {
        if (path == null || path.isEmpty()) return path;
        String[] segments = path.split("/", -1);
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < segments.length; i++) {
            if (i > 0) result.append("/");
            result.append(URLEncoder.encode(segments[i], StandardCharsets.UTF_8).replace("+", "%20"));
        }
        return result.toString();
    }
}
