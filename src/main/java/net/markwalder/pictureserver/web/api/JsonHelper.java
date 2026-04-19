package net.markwalder.pictureserver.web.api;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sun.net.httpserver.HttpExchange;

final class JsonHelper {

    static final ObjectMapper MAPPER = JsonMapper.builder()
            .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
            .enable(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
            .disable(MapperFeature.DEFAULT_VIEW_INCLUSION)
            .build();

    private JsonHelper() {
    }

    static void sendJson(HttpExchange exchange, int status, Object data) throws IOException {
        byte[] body = MAPPER.writeValueAsBytes(data);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    static <T> T readJson(HttpExchange exchange, Class<T> type) throws JsonProcessingException, IOException {
        byte[] bytes = exchange.getRequestBody().readAllBytes();
        return MAPPER.readValue(bytes, type);
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

    static String getSourceIp(HttpExchange exchange) {
        return exchange.getRemoteAddress().getAddress().getHostAddress();
    }

    static String getUserAgent(HttpExchange exchange) {
        String ua = exchange.getRequestHeaders().getFirst("User-Agent");
        return ua != null ? ua : "unknown";
    }
}
