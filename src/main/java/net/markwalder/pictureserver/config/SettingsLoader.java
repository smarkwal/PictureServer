package net.markwalder.pictureserver.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class SettingsLoader {

    private SettingsLoader() {
    }

    public static Settings load(Path settingsFile, Path cwd) throws IOException {
        if (!Files.exists(settingsFile)) {
            throw new IllegalStateException("settings.properties not found in current working directory: " + settingsFile);
        }

        Properties props = new Properties();
        try (InputStream input = Files.newInputStream(settingsFile)) {
            props.load(input);
        }

        String pathValue = asNonBlankString(props.getProperty("path"), "path");
        String username = asNonBlankString(props.getProperty("username"), "username");
        String password = asNonBlankString(props.getProperty("password"), "password");
        int port = asPort(props.getProperty("port"));

        Path root = Path.of(pathValue);
        if (!root.isAbsolute()) {
            root = cwd.resolve(root);
        }
        root = root.normalize().toAbsolutePath();

        if (!Files.isDirectory(root)) {
            throw new IllegalStateException("Configured path is not an existing directory: " + root);
        }

        return new Settings(root, port, username, password);
    }

    private static String asNonBlankString(String value, String key) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException("Missing or invalid '" + key + "' in settings.properties");
        }
        return value.trim();
    }

    private static int asPort(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException("Missing or invalid 'port' in settings.properties");
        }
        int port;
        try {
            port = Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalStateException("Invalid 'port' in settings.properties", ex);
        }

        if (port < 1 || port > 65535) {
            throw new IllegalStateException("Port must be between 1 and 65535");
        }
        return port;
    }
}
