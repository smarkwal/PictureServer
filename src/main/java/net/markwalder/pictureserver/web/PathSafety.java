package net.markwalder.pictureserver.web;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

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

    private static String decode(String path) {
        return URLDecoder.decode(path == null ? "" : path, StandardCharsets.UTF_8);
    }
}
