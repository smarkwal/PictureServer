package net.markwalder.pictureserver.web.api;

import java.io.IOException;
import java.util.Map;

import com.sun.net.httpserver.HttpExchange;

final class ShutdownApiHandler {

    private final Runnable shutdownAction;

    ShutdownApiHandler(Runnable shutdownAction) {
        this.shutdownAction = shutdownAction;
    }

    void handle(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            JsonHelper.sendJson(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }
        JsonHelper.sendJson(exchange, 200, Map.of("success", true));
        new Thread(shutdownAction, "shutdown-server-thread").start();
    }
}
