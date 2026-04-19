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

        Path settingsFile = tempDir.resolve("settings.properties");
        Files.writeString(settingsFile, """
                path=./pics
                port=9090
                username=alice
                password=secret
                """);

        Settings settings = SettingsLoader.load(settingsFile, tempDir);

        assertEquals(pictures.toAbsolutePath().normalize(), settings.rootDirectory());
        assertEquals(9090, settings.port());
        assertEquals("alice", settings.username());
        assertEquals("secret", settings.password());
    }

    @Test
    void rejectsPortOutsideRange() throws IOException {
        Path pictures = tempDir.resolve("pics");
        Files.createDirectories(pictures);

        Path settingsFile = tempDir.resolve("settings.properties");
        Files.writeString(settingsFile, """
                path=./pics
                port=99999
                username=alice
                password=secret
                """);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> SettingsLoader.load(settingsFile, tempDir));

        assertEquals("Port must be between 1 and 65535", exception.getMessage());
    }

    @Test
    void rejectsMissingUsername() throws IOException {
        Path pictures = tempDir.resolve("pics");
        Files.createDirectories(pictures);

        Path settingsFile = tempDir.resolve("settings.properties");
        Files.writeString(settingsFile, """
                path=./pics
                port=8080
                password=secret
                """);

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> SettingsLoader.load(settingsFile, tempDir));

        assertEquals("Missing or invalid 'username' in settings.properties", exception.getMessage());
    }

    @Test
    void rejectsMissingPassword() throws IOException {
        Path pictures = tempDir.resolve("pics");
        Files.createDirectories(pictures);

        Path settingsFile = tempDir.resolve("settings.properties");
        Files.writeString(settingsFile, """
                path=./pics
                port=8080
                username=alice
                """);

        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> SettingsLoader.load(settingsFile, tempDir));

        assertEquals("Missing or invalid 'password' in settings.properties", exception.getMessage());
    }
}
