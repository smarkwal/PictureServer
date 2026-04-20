package net.markwalder.pictureserver.web.api;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.Executor;

final class ShutdownApiHandler {

    private final Runnable shutdownAction;
    private final Executor shutdownExecutor;

    ShutdownApiHandler(Runnable shutdownAction) {
        this(shutdownAction, command -> new Thread(command, "shutdown-server-thread").start());
    }

    ShutdownApiHandler(Runnable shutdownAction, Executor shutdownExecutor) {
        this.shutdownAction = shutdownAction;
        this.shutdownExecutor = shutdownExecutor;
    }

    void handle(HttpExchange exchange) throws IOException {
        if (!"POST".equals(exchange.getRequestMethod())) {
            JsonHelper.sendJson(exchange, 405, Map.of("error", "Method not allowed"));
            return;
        }

        // Send response
        JsonHelper.sendJson(exchange, 200, Map.of("success", true));

        // Trigger shutdown asynchronously
        shutdownExecutor.execute(shutdownAction);
    }
}
