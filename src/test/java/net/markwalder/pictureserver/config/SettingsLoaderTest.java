package net.markwalder.pictureserver.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SettingsLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void loadsRelativePathFromWorkingDirectory() throws IOException {
        Path pictures = tempDir.resolve("pics");
        Files.createDirectories(pictures);

        Path settingsFile = tempDir.resolve("settings.yaml");
        Files.writeString(settingsFile, """
                path: ./pics
                port: 9090
                password: secret
                """);

        Settings settings = SettingsLoader.load(settingsFile, tempDir);

        assertEquals(pictures.toAbsolutePath().normalize(), settings.rootDirectory());
        assertEquals(9090, settings.port());
        assertEquals("secret", settings.password());
    }

    @Test
    void rejectsPortOutsideRange() throws IOException {
        Path pictures = tempDir.resolve("pics");
        Files.createDirectories(pictures);

        Path settingsFile = tempDir.resolve("settings.yaml");
        Files.writeString(settingsFile, """
                path: ./pics
                port: 99999
                password: secret
                """);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> SettingsLoader.load(settingsFile, tempDir));

        assertEquals("Port must be between 1 and 65535", exception.getMessage());
    }

    @Test
    void rejectsMissingPassword() throws IOException {
        Path pictures = tempDir.resolve("pics");
        Files.createDirectories(pictures);

        Path settingsFile = tempDir.resolve("settings.yaml");
        Files.writeString(settingsFile, """
                path: ./pics
                port: 8080
                """);

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> SettingsLoader.load(settingsFile, tempDir));

        assertEquals("Missing or invalid 'password' in settings.yaml", exception.getMessage());
    }
}
