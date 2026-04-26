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
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import net.markwalder.pictureserver.auth.SessionManager;
import net.markwalder.pictureserver.config.Settings.PanicSettings;
import net.markwalder.pictureserver.security.PanicMonitor;
import net.markwalder.pictureserver.web.service.PictureRepository;
import net.markwalder.pictureserver.web.service.PictureRepository.AlbumInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AlbumApiHandlerTest {

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

    private AlbumApiHandler handler;

    @BeforeEach
    void setUp() throws Exception {
        PanicMonitor panicMonitor = new PanicMonitor(PANIC_SETTINGS, new SessionManager(), () -> {});
        handler = new AlbumApiHandler(repository, panicMonitor);

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
    void handle_rejectsNonGetMethod() throws IOException {
        // Arrange
        when(exchange.getRequestMethod()).thenReturn("POST");

        // Act
        handler.handle(exchange, "/");

        // Assert
        assertThat(responseStatus.get()).isEqualTo(405);
    }

    @Test
    void handle_returns403ForPathTraversalAttempt() throws IOException {
        // Arrange
        when(exchange.getRequestMethod()).thenReturn("GET");
        when(repository.getAlbumInfo("/../etc/passwd")).thenThrow(new SecurityException());

        // Act
        handler.handle(exchange, "/../etc/passwd");

        // Assert
        assertThat(responseStatus.get()).isEqualTo(403);
    }

    @Test
    void handle_returns404WhenAlbumDoesNotExist() throws IOException {
        // Arrange
        when(exchange.getRequestMethod()).thenReturn("GET");
        when(repository.getAlbumInfo("/missing-album")).thenReturn(Optional.empty());

        // Act
        handler.handle(exchange, "/missing-album");

        // Assert
        assertThat(responseStatus.get()).isEqualTo(404);
    }

    @Test
    void handle_returnsAlbumResponseForExistingDirectory() throws IOException {
        // Arrange
        AlbumInfo albumInfo = new AlbumInfo(
                List.of("Cities", "Trips 2024"),
                Map.of("Cities", "tokyo.png", "Trips 2024", "beach photo.jpg"),
                List.of("cover.jpg"));
        when(exchange.getRequestMethod()).thenReturn("GET");
        when(repository.getAlbumInfo("/")).thenReturn(Optional.of(albumInfo));

        // Act
        handler.handle(exchange, "/");

        // Assert
        String body = responseBodyOut.toString(StandardCharsets.UTF_8);
        assertThat(responseStatus.get()).isEqualTo(200);
        assertThat(body).contains("\"path\":\"/\"");
        assertThat(body).contains("\"albums\":[\"Cities\",\"Trips 2024\"]");
        assertThat(body).contains("\"pictures\":[\"cover.jpg\"]");
        assertThat(body).contains("\"Cities\":\"/api/images/Cities/tokyo.png\"");
        assertThat(body).contains("\"Trips 2024\":\"/api/images/Trips%202024/beach%20photo.jpg\"");
    }
}
