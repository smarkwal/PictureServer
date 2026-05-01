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
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import net.markwalder.pictureserver.auth.SessionManager;
import net.markwalder.pictureserver.config.Settings.PanicSettings;
import net.markwalder.pictureserver.security.PanicMonitor;
import net.markwalder.pictureserver.web.service.PictureRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FavoritesApiHandlerTest {

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

    private FavoritesApiHandler handler;

    @BeforeEach
    void setUp() throws Exception {
        PanicMonitor panicMonitor = new PanicMonitor(PANIC_SETTINGS, new SessionManager(), () -> {});
        handler = new FavoritesApiHandler(repository, panicMonitor);

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
    void handleAdd_returns404ForNonImageFile() throws IOException {
        // Arrange
        when(repository.addFavorite("/notes.txt")).thenReturn(Optional.empty());

        // Act
        handler.handleAdd(exchange, "/notes.txt");

        // Assert
        assertThat(responseStatus.get()).isEqualTo(404);
    }

    @Test
    void handleAdd_returns200WithFavoriteTrueWhenAdded() throws IOException {
        // Arrange
        when(repository.addFavorite("/photo.jpg")).thenReturn(Optional.of(true));

        // Act
        handler.handleAdd(exchange, "/photo.jpg");

        // Assert
        String body = responseBodyOut.toString(StandardCharsets.UTF_8);
        assertThat(responseStatus.get()).isEqualTo(200);
        assertThat(body).contains("\"favorite\":true");
    }

    @Test
    void handleAdd_returns200WithFavoriteTrueWhenAlreadyPresent() throws IOException {
        // Arrange
        when(repository.addFavorite("/photo.jpg")).thenReturn(Optional.of(false));

        // Act
        handler.handleAdd(exchange, "/photo.jpg");

        // Assert
        String body = responseBodyOut.toString(StandardCharsets.UTF_8);
        assertThat(responseStatus.get()).isEqualTo(200);
        assertThat(body).contains("\"favorite\":true");
    }

    @Test
    void handleAdd_returns403ForPathTraversalAttempt() throws IOException {
        // Arrange
        when(repository.addFavorite("/../etc/passwd")).thenThrow(new SecurityException());

        // Act
        handler.handleAdd(exchange, "/../etc/passwd");

        // Assert
        assertThat(responseStatus.get()).isEqualTo(403);
    }

    @Test
    void handleAdd_stripsFavoritesPrefixBeforeCalling() throws IOException {
        // Arrange
        when(repository.addFavorite("/vacation/beach.jpg")).thenReturn(Optional.of(true));

        // Act
        handler.handleAdd(exchange, "/Favorites/vacation/beach.jpg");

        // Assert
        assertThat(responseStatus.get()).isEqualTo(200);
    }

    @Test
    void handleRemove_returns404ForNonImageFile() throws IOException {
        // Arrange
        when(repository.removeFavorite("/notes.txt")).thenReturn(Optional.empty());

        // Act
        handler.handleRemove(exchange, "/notes.txt");

        // Assert
        assertThat(responseStatus.get()).isEqualTo(404);
    }

    @Test
    void handleRemove_returns200WithFavoriteFalseWhenRemoved() throws IOException {
        // Arrange
        when(repository.removeFavorite("/photo.jpg")).thenReturn(Optional.of(true));

        // Act
        handler.handleRemove(exchange, "/photo.jpg");

        // Assert
        String body = responseBodyOut.toString(StandardCharsets.UTF_8);
        assertThat(responseStatus.get()).isEqualTo(200);
        assertThat(body).contains("\"favorite\":false");
    }

    @Test
    void handleRemove_returns403ForPathTraversalAttempt() throws IOException {
        // Arrange
        when(repository.removeFavorite("/../etc/passwd")).thenThrow(new SecurityException());

        // Act
        handler.handleRemove(exchange, "/../etc/passwd");

        // Assert
        assertThat(responseStatus.get()).isEqualTo(403);
    }

    @Test
    void handleRemove_stripsFavoritesPrefixBeforeCalling() throws IOException {
        // Arrange
        when(repository.removeFavorite("/vacation/beach.jpg")).thenReturn(Optional.of(true));

        // Act
        handler.handleRemove(exchange, "/Favorites/vacation/beach.jpg");

        // Assert
        assertThat(responseStatus.get()).isEqualTo(200);
    }
}
