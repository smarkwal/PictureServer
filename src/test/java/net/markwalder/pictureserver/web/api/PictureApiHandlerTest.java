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
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import net.markwalder.pictureserver.auth.SessionManager;
import net.markwalder.pictureserver.config.Settings.PanicSettings;
import net.markwalder.pictureserver.security.PanicMonitor;
import net.markwalder.pictureserver.web.service.PictureRepository;
import net.markwalder.pictureserver.web.service.PictureRepository.PictureInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PictureApiHandlerTest {

    private static final PanicSettings PANIC_SETTINGS =
            new PanicSettings(true, true, true, 5, 60, 5, 60, 10, 60, 5, 60);

    @Mock
    HttpExchange exchange;

    @Mock
    PictureRepository repository;

    private final Headers requestHeaders = new Headers();
    private final Headers responseHeaders = new Headers();
    private final AtomicInteger responseStatus = new AtomicInteger(-1);
    private ByteArrayOutputStream responseBodyOut;

    private PictureApiHandler handler;

    @BeforeEach
    void setUp() throws Exception {
        PanicMonitor panicMonitor = new PanicMonitor(PANIC_SETTINGS, new SessionManager(), () -> {});
        handler = new PictureApiHandler(repository, panicMonitor);

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

    @Test
    void handleGet_returns403ForPathTraversalAttempt() throws IOException {
        // Arrange
        when(repository.getPictureInfo("/../etc/passwd")).thenThrow(new SecurityException());

        // Act
        handler.handleGet(exchange, "/../etc/passwd");

        // Assert
        assertThat(responseStatus.get()).isEqualTo(403);
    }

    @Test
    void handleGet_returns404ForMissingPicture() throws IOException {
        // Arrange
        when(repository.getPictureInfo("/missing.jpg")).thenReturn(Optional.empty());

        // Act
        handler.handleGet(exchange, "/missing.jpg");

        // Assert
        assertThat(responseStatus.get()).isEqualTo(404);
    }

    @Test
    void handleGet_returnsPictureResponseWithSiblings() throws IOException {
        // Arrange
        PictureInfo pictureInfo = new PictureInfo(List.of("a.jpg", "b photo.jpg"));
        when(repository.getPictureInfo("/My%20Album/b%20photo.jpg")).thenReturn(Optional.of(pictureInfo));

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
        when(repository.moveToTrash("/../etc/passwd")).thenThrow(new SecurityException());

        // Act
        handler.handleDelete(exchange, "/../etc/passwd");

        // Assert
        assertThat(responseStatus.get()).isEqualTo(403);
    }

    @Test
    void handleDelete_returns404ForNonImageFile() throws IOException {
        // Arrange
        when(repository.moveToTrash("/notes.txt")).thenReturn(Optional.empty());

        // Act
        handler.handleDelete(exchange, "/notes.txt");

        // Assert
        assertThat(responseStatus.get()).isEqualTo(404);
    }

    @Test
    void handleDelete_returns500WhenMoveToTrashNotSupported() throws IOException {
        // Arrange
        when(repository.moveToTrash("/photo.jpg")).thenReturn(Optional.of(false));

        // Act
        handler.handleDelete(exchange, "/photo.jpg");

        // Assert
        assertThat(responseStatus.get()).isEqualTo(500);
    }

    @Test
    void handleDelete_returns200WhenMoveToTrashSucceeds() throws IOException {
        // Arrange
        when(repository.moveToTrash("/photo.jpg")).thenReturn(Optional.of(true));

        // Act
        handler.handleDelete(exchange, "/photo.jpg");

        // Assert
        String body = responseBodyOut.toString(StandardCharsets.UTF_8);
        assertThat(responseStatus.get()).isEqualTo(200);
        assertThat(body).contains("\"success\":true");
    }
}
