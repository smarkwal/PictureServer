package net.markwalder.pictureserver.web;

import com.sun.net.httpserver.HttpExchange;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public final class CacheHelper {

    private static final DateTimeFormatter HTTP_DATE_FORMATTER = DateTimeFormatter.RFC_1123_DATE_TIME;

    private CacheHelper() {
    }

    public static String buildETag(long size, long lastModifiedMillis) {
        return "\"" + Long.toHexString(size) + '-' + Long.toHexString(lastModifiedMillis) + "\"";
    }

    public static String formatHttpDate(long lastModifiedMillis) {
        return HTTP_DATE_FORMATTER.format(ZonedDateTime.ofInstant(Instant.ofEpochMilli(lastModifiedMillis), ZoneOffset.UTC));
    }

    public static boolean isNotModified(HttpExchange exchange, String eTag, long lastModifiedMillis) {
        String ifNoneMatch = exchange.getRequestHeaders().getFirst("If-None-Match");
        if (ifNoneMatch != null) {
            return eTagMatches(ifNoneMatch, eTag);
        }

        String ifModifiedSince = exchange.getRequestHeaders().getFirst("If-Modified-Since");
        if (ifModifiedSince == null) {
            return false;
        }

        try {
            long requestTimeMillis = ZonedDateTime.parse(ifModifiedSince, HTTP_DATE_FORMATTER)
                    .toInstant()
                    .toEpochMilli();
            long fileTimeMillis = (lastModifiedMillis / 1000L) * 1000L;
            return fileTimeMillis <= requestTimeMillis;
        } catch (DateTimeParseException ex) {
            return false;
        }
    }

    private static boolean eTagMatches(String ifNoneMatch, String currentETag) {
        String normalizedCurrentETag = normalizeETag(currentETag);
        for (String candidate : ifNoneMatch.split(",")) {
            String normalizedCandidate = normalizeETag(candidate.trim());
            if ("*".equals(normalizedCandidate) || normalizedCurrentETag.equals(normalizedCandidate)) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeETag(String eTag) {
        String normalized = eTag.trim();
        if (normalized.startsWith("W/")) {
            return normalized.substring(2).trim();
        }
        return normalized;
    }
}
