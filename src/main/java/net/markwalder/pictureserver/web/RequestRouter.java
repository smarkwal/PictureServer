package net.markwalder.pictureserver.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
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
            // Extract request metadata
            String path = exchange.getRequestURI().getPath();
            String sourceIp = exchange.getRemoteAddress().getAddress().getHostAddress();
            String userAgentHeader = exchange.getRequestHeaders().getFirst("User-Agent");
            String userAgent = userAgentHeader != null ? userAgentHeader : "unknown";

            // Check for known attack path patterns
            panicMonitor.checkPath(path, sourceIp, userAgent);

            // Route to handler
            if (path.startsWith("/assets/") || "/assets".equals(path)) {
                StaticAssetHandler.handle(exchange);
            } else if (path.startsWith("/api/") || "/api".equals(path)) {
                apiRouter.handle(exchange);
            } else {
                StaticAssetHandler.sendIndex(exchange);
            }
        }
    }
}
