package net.markwalder.pictureserver.web.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import net.markwalder.pictureserver.auth.SessionManager;
import net.markwalder.pictureserver.config.Settings;
import net.markwalder.pictureserver.security.PanicMonitor;
import net.markwalder.pictureserver.security.ThreatEvent;

final class AuthApiHandler {

    private record LoginRequest(String username, String password) {
        @JsonCreator
        LoginRequest(
                @JsonProperty(value = "username", required = true) String username,
                @JsonProperty(value = "password", required = true) String password) {
            this.username = username;
            this.password = password;
        }
    }

    private final Settings settings;
    private final SessionManager sessionManager;
    private final PanicMonitor panicMonitor;

    AuthApiHandler(Settings settings, SessionManager sessionManager, PanicMonitor panicMonitor) {
        this.settings = settings;
        this.sessionManager = sessionManager;
        this.panicMonitor = panicMonitor;
    }

    void handleLogin(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            JsonHelper.sendJson(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }

        String sourceIp = HttpHelper.getSourceIp(exchange);
        String userAgent = HttpHelper.getUserAgent(exchange);

        LoginRequest request;
        try {
            request = JsonHelper.readJson(exchange, LoginRequest.class);
        } catch (JsonProcessingException ex) {
            panicMonitor.recordEvent(ThreatEvent.INVALID_REQUEST, sourceIp, userAgent);
            JsonHelper.sendJson(exchange, 400, Map.of("error", "Invalid request"));
            return;
        }

        if (!settings.username().equals(request.username()) || !settings.password().equals(request.password())) {
            panicMonitor.recordEvent(ThreatEvent.FAILED_LOGIN, sourceIp, userAgent);
            JsonHelper.sendJson(exchange, 401, Map.of("success", false, "error", "Invalid credentials"));
            return;
        }

        String sessionId = sessionManager.createSession(request.username(), sourceIp, userAgent);
        exchange.getResponseHeaders().add("Set-Cookie",
                sessionManager.cookieName() + "=" + sessionId + "; Path=/; HttpOnly; SameSite=Strict");
        JsonHelper.sendJson(exchange, 200, Map.of("success", true));
    }

    void handleLogout(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            JsonHelper.sendJson(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }
        Optional<String> sessionId = HttpHelper.readCookie(exchange, sessionManager.cookieName());
        sessionId.ifPresent(sessionManager::removeSession);
        exchange.getResponseHeaders().add("Set-Cookie",
                sessionManager.cookieName() + "=; Path=/; HttpOnly; SameSite=Strict; Max-Age=0");
        JsonHelper.sendJson(exchange, 200, Map.of());
    }
}
