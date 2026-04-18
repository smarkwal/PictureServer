package net.markwalder.pictureserver;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpServer;

import net.markwalder.pictureserver.auth.SessionManager;
import net.markwalder.pictureserver.config.Settings;
import net.markwalder.pictureserver.config.SettingsLoader;
import net.markwalder.pictureserver.web.HtmlRenderer;
import net.markwalder.pictureserver.web.PictureServerHandler;

public final class Main {

    private Main() {
    }

    public static void main(String[] args) throws IOException {
        Path cwd = Path.of(System.getProperty("user.dir"));
        Path settingsFile = cwd.resolve("settings.yaml");

        Settings settings = SettingsLoader.load(settingsFile, cwd);

        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", settings.port()), 0);
        PictureServerHandler handler = new PictureServerHandler(settings, new SessionManager(), new HtmlRenderer());

        server.createContext("/", handler);
        server.setExecutor(Executors.newFixedThreadPool(16));

        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(0)));

        server.start();
        System.out.printf("Picture server listening on http://0.0.0.0:%d with root %s%n", settings.port(), settings.rootDirectory());
    }
}
