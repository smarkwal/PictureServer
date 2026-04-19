package net.markwalder.pictureserver;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpServer;

import net.markwalder.pictureserver.auth.SessionManager;
import net.markwalder.pictureserver.config.Settings;
import net.markwalder.pictureserver.config.SettingsLoader;
import net.markwalder.pictureserver.security.PanicMonitor;
import net.markwalder.pictureserver.web.RequestRouter;
import net.markwalder.pictureserver.web.api.ApiRouter;

public final class Main {

    private Main() {
    }

    public static void main(String[] args) throws IOException {
        Path cwd = Path.of(System.getProperty("user.dir"));
        Path settingsFile = cwd.resolve("settings.properties");

        Settings settings = SettingsLoader.load(settingsFile, cwd);

        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", settings.port()), 0);
        SessionManager sessionManager = new SessionManager();
        Runnable shutdownAction = () -> {
            server.stop(0);
            System.exit(0);
        };
        PanicMonitor panicMonitor = new PanicMonitor(settings.panic(), sessionManager, shutdownAction);
        ApiRouter apiRouter = new ApiRouter(settings, sessionManager, panicMonitor, shutdownAction);
        RequestRouter requestRouter = new RequestRouter(panicMonitor, apiRouter);

        server.createContext("/", requestRouter);
        server.setExecutor(Executors.newFixedThreadPool(16));

        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(0)));

        server.start();
        Logger.log("Picture server listening on http://0.0.0.0:%d with root %s", settings.port(), settings.rootDirectory());
    }
}
