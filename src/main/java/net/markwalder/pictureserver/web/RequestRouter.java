package net.markwalder.pictureserver.web;

import java.io.IOException;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import net.markwalder.pictureserver.security.PanicMonitor;
import net.markwalder.pictureserver.web.api.ApiRouter;

public final class RequestRouter implements HttpHandler {

    private final PanicMonitor panicMonitor;
    private final ApiRouter apiRouter;

    public RequestRouter(PanicMonitor panicMonitor, ApiRouter apiRouter) {
        this.panicMonitor = panicMonitor;
        this.apiRouter = apiRouter;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            String path = exchange.getRequestURI().getPath();
            String sourceIp = exchange.getRemoteAddress().getAddress().getHostAddress();
            String userAgentHeader = exchange.getRequestHeaders().getFirst("User-Agent");
            String userAgent = userAgentHeader != null ? userAgentHeader : "unknown";

            panicMonitor.checkPath(path, sourceIp, userAgent);

            if (path.startsWith("/assets/") || "/assets".equals(path)) {
                StaticAssetHandler.handle(exchange);
            } else if (path.startsWith("/api/") || "/api".equals(path)) {
                apiRouter.handle(exchange, sourceIp, userAgent);
            } else {
                StaticAssetHandler.sendIndex(exchange);
            }
        }
    }
}
