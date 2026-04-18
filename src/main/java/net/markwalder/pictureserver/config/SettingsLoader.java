package net.markwalder.pictureserver.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.yaml.snakeyaml.Yaml;

public final class SettingsLoader {

    private SettingsLoader() {
    }

    public static Settings load(Path settingsFile, Path cwd) throws IOException {
        if (!Files.exists(settingsFile)) {
            throw new IllegalStateException("settings.yaml not found in current working directory: " + settingsFile);
        }

        Map<String, Object> map;
        try (InputStream input = Files.newInputStream(settingsFile)) {
            Yaml yaml = new Yaml();
            Object parsed = yaml.load(input);
            if (!(parsed instanceof Map<?, ?> rawMap)) {
                throw new IllegalStateException("settings.yaml must contain key-value pairs");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> cast = (Map<String, Object>) rawMap;
            map = cast;
        }

        String pathValue = asNonBlankString(map.get("path"), "path");
        String username = asNonBlankString(map.get("username"), "username");
        String password = asNonBlankString(map.get("password"), "password");
        int port = asPort(map.get("port"));

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

    private static String asNonBlankString(Object value, String key) {
        if (!(value instanceof String stringValue)) {
            throw new IllegalStateException("Missing or invalid '" + key + "' in settings.yaml");
        }
        String trimmed = stringValue.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalStateException("Missing or invalid '" + key + "' in settings.yaml");
        }
        return trimmed;
    }

    private static int asPort(Object value) {
        int port = switch (value) {
            case Number number -> number.intValue();
            case String stringPort -> {
                try {
                    yield Integer.parseInt(stringPort.trim());
                } catch (NumberFormatException ex) {
                    throw new IllegalStateException("Invalid 'port' in settings.yaml", ex);
                }
            }
            default -> throw new IllegalStateException("Missing or invalid 'port' in settings.yaml");
        };

        if (port < 1 || port > 65535) {
            throw new IllegalStateException("Port must be between 1 and 65535");
        }
        return port;
    }
}
