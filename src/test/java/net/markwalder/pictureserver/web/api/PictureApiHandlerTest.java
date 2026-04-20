package net.markwalder.pictureserver.web.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.markwalder.pictureserver.auth.SessionManager;
import net.markwalder.pictureserver.config.Settings;
import net.markwalder.pictureserver.config.Settings.PanicSettings;
import net.markwalder.pictureserver.security.PanicMonitor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PictureApiHandlerTest {

    private static final PanicSettings PANIC_SETTINGS =
            new PanicSettings(true, true, true, 5, 60, 5, 60, 10, 60, 5, 60);

    @TempDir
    Path rootDir;

    @Mock
    HttpExchange exchange;

    private final Headers requestHeaders = new Headers();
    private final Headers responseHeaders = new Headers();
    private final AtomicInteger responseStatus = new AtomicInteger(-1);
    private ByteArrayOutputStream responseBodyOut;

    private PanicMonitor panicMonitor;

    @BeforeEach
    void setUp() throws Exception {
        panicMonitor = new PanicMonitor(PANIC_SETTINGS, new SessionManager(), () -> {});

        responseBodyOut = new ByteArrayOutputStream();
        InetSocketAddress remoteAddress = new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0);
        requestHeaders.set("User-Agent", "TestAgent/1.0");

        when(exchange.getRequestHeaders()).thenReturn(requestHeaders);
        when(exchange.getResponseHeaders()).thenReturn(responseHeaders);
        when(exchange.getResponseBody()).thenReturn(responseBodyOut);
        when(exchange.getRemoteAddress()).thenReturn(remoteAddress);
        doAnswer(inv -> {
            responseStatus.set(inv.getArgument(0));
            return null;
        }).when(exchange).sendResponseHeaders(anyInt(), anyLong());
    }

    private PictureApiHandler createHandler(PictureApiHandler.TrashMover trashMover) {
        Settings settings = new Settings(rootDir, 8080, "admin", "secret", PANIC_SETTINGS);
        return new PictureApiHandler(settings, panicMonitor, trashMover);
    }

    @Test
    void handleGet_returns403ForPathTraversalAttempt() throws IOException {
        // Arrange
        PictureApiHandler handler = createHandler(path -> true);

        // Act
        handler.handleGet(exchange, "/../etc/passwd");

        // Assert
        assertThat(responseStatus.get()).isEqualTo(403);
    }

    @Test
    void handleGet_returns404ForMissingPicture() throws IOException {
        // Arrange
        PictureApiHandler handler = createHandler(path -> true);

        // Act
        handler.handleGet(exchange, "/missing.jpg");

        // Assert
        assertThat(responseStatus.get()).isEqualTo(404);
    }

    @Test
    void handleGet_returnsPictureResponseWithSiblings() throws IOException {
        // Arrange
        Path albumDir = rootDir.resolve("My Album");
        Files.createDirectories(albumDir);
        Files.write(albumDir.resolve("a.jpg"), new byte[] {1});
        Files.write(albumDir.resolve("b photo.jpg"), new byte[] {2});
        PictureApiHandler handler = createHandler(path -> true);

        // Act
        handler.handleGet(exchange, "/My%20Album/b%20photo.jpg");

        // Assert
        String body = responseBodyOut.toString(StandardCharsets.UTF_8);
        assertThat(responseStatus.get()).isEqualTo(200);
        assertThat(body).contains("\"name\":\"b photo.jpg\"");
        assertThat(body).contains("\"src\":\"/api/images/My%20Album/b%20photo.jpg\"");
        assertThat(body).contains("/My%20Album/a.jpg");
        assertThat(body).contains("/My%20Album/b%20photo.jpg");
    }

    @Test
    void handleDelete_returns403ForPathTraversalAttempt() throws IOException {
        // Arrange
        PictureApiHandler handler = createHandler(path -> true);

        // Act
        handler.handleDelete(exchange, "/../etc/passwd");

        // Assert
        assertThat(responseStatus.get()).isEqualTo(403);
    }

    @Test
    void handleDelete_returns404ForNonImageFile() throws IOException {
        // Arrange
        Files.write(rootDir.resolve("notes.txt"), "hello".getBytes(StandardCharsets.UTF_8));
        PictureApiHandler handler = createHandler(path -> true);

        // Act
        handler.handleDelete(exchange, "/notes.txt");

        // Assert
        assertThat(responseStatus.get()).isEqualTo(404);
    }

    @Test
    void handleDelete_returns500WhenMoveToTrashNotSupported() throws IOException {
        // Arrange
        Files.write(rootDir.resolve("photo.jpg"), new byte[] {1});
        PictureApiHandler handler = createHandler(path -> false);

        // Act
        handler.handleDelete(exchange, "/photo.jpg");

        // Assert
        assertThat(responseStatus.get()).isEqualTo(500);
    }

    @Test
    void handleDelete_returns200WhenMoveToTrashSucceeds() throws IOException {
        // Arrange
        Path imagePath = rootDir.resolve("photo.jpg");
        Files.write(imagePath, new byte[] {1});
        AtomicReference<Path> movedPath = new AtomicReference<>();
        PictureApiHandler handler = createHandler(path -> {
            movedPath.set(path);
            return true;
        });

        // Act
        handler.handleDelete(exchange, "/photo.jpg");

        // Assert
        String body = responseBodyOut.toString(StandardCharsets.UTF_8);
        assertThat(responseStatus.get()).isEqualTo(200);
        assertThat(body).contains("\"success\":true");
        assertThat(movedPath.get()).isEqualTo(imagePath);
    }
}
