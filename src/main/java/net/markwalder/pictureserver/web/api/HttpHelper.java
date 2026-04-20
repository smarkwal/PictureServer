package net.markwalder.pictureserver.web.api;

import com.sun.net.httpserver.HttpExchange;
import java.util.List;
import java.util.Optional;

final class HttpHelper {

    private HttpHelper() {
    }

    static String getSourceIp(HttpExchange exchange) {
        return exchange.getRemoteAddress().getAddress().getHostAddress();
    }

    static String getUserAgent(HttpExchange exchange) {
        String ua = exchange.getRequestHeaders().getFirst("User-Agent");
        return ua != null ? ua : "unknown";
    }

    static Optional<String> readCookie(HttpExchange exchange, String key) {
        List<String> cookieHeaders = exchange.getRequestHeaders().getOrDefault("Cookie", List.of());
        for (String header : cookieHeaders) {
            for (String part : header.split(";")) {
                String[] pair = part.trim().split("=", 2);
                if (pair.length == 2 && key.equals(pair[0])) {
                    return Optional.of(pair[1]);
                }
            }
        }
        return Optional.empty();
    }
}
