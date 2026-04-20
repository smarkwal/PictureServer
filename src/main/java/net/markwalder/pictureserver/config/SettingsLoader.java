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

        // Load default properties
        Properties defaults = new Properties();
        try (InputStream input = SettingsLoader.class.getResourceAsStream("/default-settings.properties")) {
            if (input != null) {
                defaults.load(input);
            }
        }

        // Load user properties
        Properties props = new Properties(defaults);
        try (InputStream input = Files.newInputStream(settingsFile)) {
            props.load(input);
        }

        // Parse connection settings
        String pathValue = asNonBlankString(props.getProperty("path"), "path");
        String username = asNonBlankString(props.getProperty("username"), "username");
        String password = asNonBlankString(props.getProperty("password"), "password");
        int port = asPort(props.getProperty("port"));

        // Resolve root directory
        Path root = Path.of(pathValue);
        if (!root.isAbsolute()) {
            root = cwd.resolve(root);
        }
        root = root.normalize().toAbsolutePath();

        if (!Files.isDirectory(root)) {
            throw new IllegalStateException("Configured path is not an existing directory: " + root);
        }

        // Build panic settings
        Settings.PanicSettings panic = new Settings.PanicSettings(
                asBoolean(props.getProperty("panic.enabled")),
                asBoolean(props.getProperty("panic.pathTraversal.enabled")),
                asBoolean(props.getProperty("panic.knownAttackProbe.enabled")),
                asPositiveInt(props.getProperty("panic.failedLogins.threshold"), "panic.failedLogins.threshold"),
                asPositiveInt(props.getProperty("panic.failedLogins.windowSeconds"), "panic.failedLogins.windowSeconds"),
                asPositiveInt(props.getProperty("panic.invalidSession.threshold"), "panic.invalidSession.threshold"),
                asPositiveInt(props.getProperty("panic.invalidSession.windowSeconds"), "panic.invalidSession.windowSeconds"),
                asPositiveInt(props.getProperty("panic.excessive404.threshold"), "panic.excessive404.threshold"),
                asPositiveInt(props.getProperty("panic.excessive404.windowSeconds"), "panic.excessive404.windowSeconds"),
                asPositiveInt(props.getProperty("panic.invalidRequest.threshold"), "panic.invalidRequest.threshold"),
                asPositiveInt(props.getProperty("panic.invalidRequest.windowSeconds"), "panic.invalidRequest.windowSeconds")
        );

        return new Settings(root, port, username, password, panic);
    }

    private static String asNonBlankString(String value, String key) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException("Missing or invalid '" + key + "' in settings.properties");
        }
        return value.trim();
    }

    private static boolean asBoolean(String value) {
        return Boolean.parseBoolean(value);
    }

    private static int asPort(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException("Missing or invalid 'port' in settings.properties");
        }

        // Parse port number
        int port;
        try {
            port = Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalStateException("Invalid 'port' in settings.properties", ex);
        }

        // Validate port range
        if (port < 1 || port > 65535) {
            throw new IllegalStateException("Port must be between 1 and 65535");
        }
        return port;
    }

    private static int asPositiveInt(String value, String key) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException("Missing or invalid '" + key + "' in settings.properties");
        }

        // Parse integer value
        int result;
        try {
            result = Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalStateException("Invalid '" + key + "' in settings.properties", ex);
        }

        // Validate positive constraint
        if (result < 1) {
            throw new IllegalStateException("Value of '" + key + "' must be at least 1");
        }
        return result;
    }
}
