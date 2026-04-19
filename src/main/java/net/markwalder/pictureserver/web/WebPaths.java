package net.markwalder.pictureserver.web;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class WebPaths {

    private WebPaths() {
    }

    public static String normalizeWebPath(String path) {
        String decoded = decode(path);
        if (decoded.isBlank() || "/".equals(decoded)) {
            return "/";
        }
        String[] parts = decoded.split("/");
        List<String> cleaned = new ArrayList<>();
        for (String part : parts) {
            if (!part.isBlank()) {
                cleaned.add(part);
            }
        }
        if (cleaned.isEmpty()) {
            return "/";
        }
        return "/" + String.join("/", cleaned);
    }

    public static String parentWebPath(String currentPath) {
        if (currentPath == null || currentPath.isBlank() || "/".equals(currentPath)) {
            return null;
        }
        int index = currentPath.lastIndexOf('/');
        if (index <= 0) {
            return "/";
        }
        return currentPath.substring(0, index);
    }

    public static String encodeWebPath(String path) {
        String normalized = normalizeWebPath(path);
        if ("/".equals(normalized)) {
            return "/";
        }

        String[] parts = normalized.split("/");
        List<String> encoded = new ArrayList<>();
        for (String part : parts) {
            if (!part.isBlank()) {
                encoded.add(URLEncoder.encode(part, StandardCharsets.UTF_8).replace("+", "%20"));
            }
        }

        return "/" + String.join("/", encoded);
    }

    private static String decode(String path) {
        return URLDecoder.decode(path == null ? "" : path, StandardCharsets.UTF_8);
    }
}
