package net.markwalder.pictureserver.web.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import net.markwalder.pictureserver.auth.SessionManager;
import net.markwalder.pictureserver.config.Settings.PanicSettings;
import net.markwalder.pictureserver.security.PanicMonitor;
import net.markwalder.pictureserver.web.CacheHelper;
import net.markwalder.pictureserver.web.service.PictureRepository;
import net.markwalder.pictureserver.web.service.PictureRepository.ImageInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ImageApiHandlerTest {

    private static final String IP = "127.0.0.1";
    private static final String UA = "TestAgent/1.0";
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

    private SessionManager sessionManager;
    private ImageApiHandler handler;

    @BeforeEach
    void setUp() throws Exception {
        sessionManager = new SessionManager();
        PanicMonitor panicMonitor = new PanicMonitor(PANIC_SETTINGS, sessionManager, () -> {});
        handler = new ImageApiHandler(repository, sessionManager, panicMonitor);

        responseBodyOut = new ByteArrayOutputStream();
        InetSocketAddress remoteAddress = new InetSocketAddress(InetAddress.getByName(IP), 0);
        requestHeaders.set("User-Agent", UA);

        when(exchange.getRequestHeaders()).thenReturn(requestHeaders);
        when(exchange.getResponseHeaders()).thenReturn(responseHeaders);
        when(exchange.getResponseBody()).thenReturn(responseBodyOut);
        when(exchange.getRemoteAddress()).thenReturn(remoteAddress);
        doAnswer(inv -> {
            responseStatus.set(inv.getArgument(0));
            return null;
        }).when(exchange).sendResponseHeaders(anyInt(), anyLong());
    }

    private void authenticateSession() {
        String sessionId = sessionManager.createSession("admin", IP, UA);
        requestHeaders.set("Cookie", "PSSESSION=" + sessionId);
    }

    @Test
    void handle_rejectsNonGetMethod() throws IOException {
        // Arrange
        when(exchange.getRequestMethod()).thenReturn("POST");

        // Act
        handler.handle(exchange, "/photo.jpg");

        // Assert
        assertThat(responseStatus.get()).isEqualTo(405);
    }

    @Test
    void handle_returns403WhenNotAuthenticated() throws IOException {
        // Arrange
        when(exchange.getRequestMethod()).thenReturn("GET");

        // Act
        handler.handle(exchange, "/photo.jpg");

        // Assert
        assertThat(responseStatus.get()).isEqualTo(403);
    }

    @Test
    void handle_returns403ForPathTraversalAttempt() throws IOException {
        // Arrange
        authenticateSession();
        when(exchange.getRequestMethod()).thenReturn("GET");
        when(repository.getImageInfo("/../etc/passwd")).thenThrow(new SecurityException());

        // Act
        handler.handle(exchange, "/../etc/passwd");

        // Assert
        assertThat(responseStatus.get()).isEqualTo(403);
    }

    @Test
    void handle_returns404ForNonImageFile() throws IOException {
        // Arrange
        authenticateSession();
        when(exchange.getRequestMethod()).thenReturn("GET");
        when(repository.getImageInfo("/notes.txt")).thenReturn(Optional.empty());

        // Act
        handler.handle(exchange, "/notes.txt");

        // Assert
        assertThat(responseStatus.get()).isEqualTo(404);
    }

    @Test
    void handle_returns304WhenResourceNotModified() throws IOException {
        // Arrange
        authenticateSession();
        long size = 3L;
        long lastModified = 1_700_000_000_000L;
        String eTag = CacheHelper.buildETag(size, lastModified);
        when(exchange.getRequestMethod()).thenReturn("GET");
        when(repository.getImageInfo("/photo.jpg")).thenReturn(Optional.of(new ImageInfo(size, lastModified, "photo.jpg")));
        requestHeaders.set("If-None-Match", eTag);

        // Act
        handler.handle(exchange, "/photo.jpg");

        // Assert
        assertThat(responseStatus.get()).isEqualTo(304);
        assertThat(responseBodyOut.toByteArray()).isEmpty();
    }

    @Test
    void handle_streamsImageAndSetsCacheHeaders() throws IOException {
        // Arrange
        authenticateSession();
        byte[] content = new byte[] {7, 8, 9};
        long size = content.length;
        long lastModified = 1_700_000_000_000L;
        when(exchange.getRequestMethod()).thenReturn("GET");
        when(repository.getImageInfo("/photo.jpg")).thenReturn(Optional.of(new ImageInfo(size, lastModified, "photo.jpg")));
        when(repository.openImage("/photo.jpg")).thenReturn(Optional.of(new ByteArrayInputStream(content)));

        // Act
        handler.handle(exchange, "/photo.jpg");

        // Assert
        assertThat(responseStatus.get()).isEqualTo(200);
        assertThat(responseHeaders.getFirst("Content-Type")).isEqualTo("image/jpeg");
        assertThat(responseHeaders.getFirst("Cache-Control")).isEqualTo("private, max-age=2592000");
        assertThat(responseHeaders.getFirst("ETag")).isNotBlank();
        assertThat(responseHeaders.getFirst("Last-Modified")).isNotBlank();
        assertThat(responseBodyOut.toByteArray()).containsExactly(content);
    }

    @Test
    void handle_stripsFavoritesPrefixAndServesRealFile() throws IOException {
        // Arrange — request uses /Favorites/ prefix; repository is called with the real path
        authenticateSession();
        byte[] content = new byte[] {1, 2, 3};
        long size = content.length;
        long lastModified = 1_700_000_000_000L;
        when(exchange.getRequestMethod()).thenReturn("GET");
        when(repository.getImageInfo("/vacation/beach.jpg")).thenReturn(Optional.of(new ImageInfo(size, lastModified, "beach.jpg")));
        when(repository.openImage("/vacation/beach.jpg")).thenReturn(Optional.of(new ByteArrayInputStream(content)));

        // Act
        handler.handle(exchange, "/Favorites/vacation/beach.jpg");

        // Assert
        assertThat(responseStatus.get()).isEqualTo(200);
        assertThat(responseBodyOut.toByteArray()).containsExactly(content);
    }
}
