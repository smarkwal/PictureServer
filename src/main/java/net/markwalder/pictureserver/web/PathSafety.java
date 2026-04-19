package net.markwalder.pictureserver.web;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class PathSafety {

    private PathSafety() {
    }

    public static Path resolveSafePath(String requestPath, Path rootDir) {
        String decoded = decode(requestPath);
        String trimmed = decoded.startsWith("/") ? decoded.substring(1) : decoded;
        if (trimmed.isEmpty()) {
            return rootDir;
        }
        Path relative = Path.of(trimmed).normalize();
        if (relative.isAbsolute() || relative.startsWith("..")) {
            throw new SecurityException("Path traversal attempt blocked.");
        }
        Path candidate = rootDir.resolve(relative).normalize();
        if (!candidate.startsWith(rootDir)) {
            throw new SecurityException("Path traversal attempt blocked.");
        }
        return candidate;
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

    private static String decode(String path) {
        return URLDecoder.decode(path == null ? "" : path, StandardCharsets.UTF_8);
    }
}
