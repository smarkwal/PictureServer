package net.markwalder.pictureserver.web.api;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

import com.sun.net.httpserver.HttpExchange;

import net.markwalder.pictureserver.auth.SessionManager;
import net.markwalder.pictureserver.config.Settings;
import net.markwalder.pictureserver.security.PanicMonitor;
import net.markwalder.pictureserver.security.ThreatEvent;

public final class ApiRouter {

    private final SessionManager sessionManager;
    private final PanicMonitor panicMonitor;
    private final SessionApiHandler sessionHandler;
    private final AuthApiHandler authHandler;
    private final AlbumApiHandler albumHandler;
    private final PictureApiHandler pictureHandler;
    private final ImageApiHandler imageHandler;
    private final ShutdownApiHandler shutdownHandler;

    public ApiRouter(Settings settings, SessionManager sessionManager, PanicMonitor panicMonitor, Runnable shutdownAction) {
        this.sessionManager = sessionManager;
        this.panicMonitor = panicMonitor;
        this.sessionHandler = new SessionApiHandler(sessionManager);
        this.authHandler = new AuthApiHandler(settings, sessionManager, panicMonitor);
        this.albumHandler = new AlbumApiHandler(settings, panicMonitor);
        this.pictureHandler = new PictureApiHandler(settings, panicMonitor);
        this.imageHandler = new ImageApiHandler(settings, sessionManager, panicMonitor);
        this.shutdownHandler = new ShutdownApiHandler(shutdownAction);
    }

    public void handle(HttpExchange exchange, String sourceIp, String userAgent) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        try {
            if ("GET".equals(method) && "/api/session".equals(path)) {
                sessionHandler.handle(exchange, sourceIp, userAgent);
                return;
            }
            if ("POST".equals(method) && "/api/login".equals(path)) {
                authHandler.handleLogin(exchange, sourceIp, userAgent);
                return;
            }
            if ("POST".equals(method) && "/api/logout".equals(path)) {
                authHandler.handleLogout(exchange);
                return;
            }

            if (!isAuthenticated(exchange, sourceIp, userAgent)) {
                JsonHelper.sendJson(exchange, 401, Map.of("error", "Not authenticated"));
                return;
            }

            if ("GET".equals(method) && path.startsWith("/api/albums")) {
                String suffix = path.substring("/api/albums".length());
                albumHandler.handle(exchange, suffix, sourceIp, userAgent);
                return;
            }
            if ("GET".equals(method) && path.startsWith("/api/pictures")) {
                String suffix = path.substring("/api/pictures".length());
                pictureHandler.handleGet(exchange, suffix, sourceIp, userAgent);
                return;
            }
            if ("DELETE".equals(method) && path.startsWith("/api/pictures")) {
                String suffix = path.substring("/api/pictures".length());
                pictureHandler.handleDelete(exchange, suffix, sourceIp, userAgent);
                return;
            }
            if ("GET".equals(method) && path.startsWith("/api/images")) {
                String suffix = path.substring("/api/images".length());
                imageHandler.handle(exchange, suffix, sourceIp, userAgent);
                return;
            }
            if ("POST".equals(method) && "/api/shutdown".equals(path)) {
                shutdownHandler.handle(exchange);
                return;
            }

            JsonHelper.sendJson(exchange, 404, Map.of("error", "Not found"));
        } catch (IOException | RuntimeException ex) {
            try {
                JsonHelper.sendJson(exchange, 500, Map.of("error", "Server error"));
            } catch (IOException ignored) {
            }
        }
    }

    private boolean isAuthenticated(HttpExchange exchange, String sourceIp, String userAgent) {
        Optional<String> cookie = JsonHelper.readCookie(exchange, sessionManager.cookieName());
        if (cookie.isEmpty()) {
            return false;
        }
        if (!sessionManager.isAuthenticated(cookie.get(), sourceIp, userAgent)) {
            panicMonitor.recordEvent(ThreatEvent.INVALID_SESSION, sourceIp, userAgent);
            return false;
        }
        return true;
    }
}
