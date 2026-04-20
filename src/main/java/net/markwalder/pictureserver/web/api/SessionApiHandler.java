package net.markwalder.pictureserver.web.api;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import net.markwalder.pictureserver.auth.SessionManager;

final class SessionApiHandler {

    private final SessionManager sessionManager;

    SessionApiHandler(SessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            JsonHelper.sendJson(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }
        Optional<String> cookie = HttpHelper.readCookie(exchange, sessionManager.cookieName());
        String sourceIp = HttpHelper.getSourceIp(exchange);
        String userAgent = HttpHelper.getUserAgent(exchange);
        boolean authenticated = cookie.isPresent() && sessionManager.isAuthenticated(cookie.get(), sourceIp, userAgent);
        JsonHelper.sendJson(exchange, 200, Map.of("authenticated", authenticated));
    }
}
