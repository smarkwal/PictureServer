package net.markwalder.pictureserver.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SettingsLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void loadsRelativePathFromWorkingDirectory() throws IOException {
        // Arrange
        Path pictures = tempDir.resolve("pics");
        Files.createDirectories(pictures);

        Path settingsFile = tempDir.resolve("settings.properties");
        Files.writeString(settingsFile, """
                path=./pics
                port=9090
                username=alice
                password=secret
                """);

        // Act
        Settings settings = SettingsLoader.load(settingsFile, tempDir);

        // Assert
        assertEquals(pictures.toAbsolutePath().normalize(), settings.rootDirectory());
        assertEquals(9090, settings.port());
        assertEquals("alice", settings.username());
        assertEquals("secret", settings.password());
    }

    @Test
    void panicSettingsDefaultsAreApplied() throws IOException {
        // Arrange
        Path pictures = tempDir.resolve("pics");
        Files.createDirectories(pictures);

        Path settingsFile = tempDir.resolve("settings.properties");
        Files.writeString(settingsFile, """
                path=./pics
                port=8080
                username=alice
                password=secret
                """);

        // Act
        Settings settings = SettingsLoader.load(settingsFile, tempDir);

        // Assert
        assertTrue(settings.panic().enabled());
        assertTrue(settings.panic().pathTraversalEnabled());
        assertTrue(settings.panic().knownAttackProbeEnabled());
        assertEquals(5, settings.panic().failedLoginsThreshold());
        assertEquals(60, settings.panic().failedLoginsWindowSeconds());
        assertEquals(5, settings.panic().invalidSessionThreshold());
        assertEquals(60, settings.panic().invalidSessionWindowSeconds());
        assertEquals(10, settings.panic().excessive404Threshold());
        assertEquals(60, settings.panic().excessive404WindowSeconds());
        assertEquals(5, settings.panic().invalidRequestThreshold());
        assertEquals(60, settings.panic().invalidRequestWindowSeconds());
    }

    @Test
    void panicSettingsCanBeOverriddenInSettingsProperties() throws IOException {
        // Arrange
        Path pictures = tempDir.resolve("pics");
        Files.createDirectories(pictures);

        Path settingsFile = tempDir.resolve("settings.properties");
        Files.writeString(settingsFile, """
                path=./pics
                port=8080
                username=alice
                password=secret
                panic.enabled=false
                panic.failedLogins.threshold=3
                panic.failedLogins.windowSeconds=30
                """);

        // Act
        Settings settings = SettingsLoader.load(settingsFile, tempDir);

        // Assert
        assertFalse(settings.panic().enabled());
        assertEquals(3, settings.panic().failedLoginsThreshold());
        assertEquals(30, settings.panic().failedLoginsWindowSeconds());
        // other defaults unchanged
        assertEquals(5, settings.panic().invalidSessionThreshold());
    }

    @Test
    void rejectsPortOutsideRange() throws IOException {
        // Arrange
        Path pictures = tempDir.resolve("pics");
        Files.createDirectories(pictures);

        Path settingsFile = tempDir.resolve("settings.properties");
        Files.writeString(settingsFile, """
                path=./pics
                port=99999
                username=alice
                password=secret
                """);

        // Act
        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> SettingsLoader.load(settingsFile, tempDir));

        // Assert
        assertEquals("Port must be between 1 and 65535", exception.getMessage());
    }

    @Test
    void rejectsMissingUsername() throws IOException {
        // Arrange
        Path pictures = tempDir.resolve("pics");
        Files.createDirectories(pictures);

        Path settingsFile = tempDir.resolve("settings.properties");
        Files.writeString(settingsFile, """
                path=./pics
                port=8080
                password=secret
                """);

        // Act
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> SettingsLoader.load(settingsFile, tempDir));

        // Assert
        assertEquals("Missing or invalid 'username' in settings.properties", exception.getMessage());
    }

    @Test
    void rejectsMissingPassword() throws IOException {
        // Arrange
        Path pictures = tempDir.resolve("pics");
        Files.createDirectories(pictures);

        Path settingsFile = tempDir.resolve("settings.properties");
        Files.writeString(settingsFile, """
                path=./pics
                port=8080
                username=alice
                """);

        // Act
        IllegalStateException exception = assertThrows(
            IllegalStateException.class,
            () -> SettingsLoader.load(settingsFile, tempDir));

        // Assert
        assertEquals("Missing or invalid 'password' in settings.properties", exception.getMessage());
    }
}
