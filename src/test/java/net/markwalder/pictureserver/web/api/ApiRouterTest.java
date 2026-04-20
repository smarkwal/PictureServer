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
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
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
class ApiRouterTest {

    private static final String IP = "127.0.0.1";
    private static final String UA = "TestAgent/1.0";
    private static final PanicSettings PANIC_SETTINGS =
            new PanicSettings(true, true, true, 5, 60, 5, 60, 10, 60, 5, 60);

    @TempDir
    Path rootDir;

    @Mock
    HttpExchange exchange;

    private final Headers requestHeaders = new Headers();
    private final Headers responseHeaders = new Headers();
    private final ByteArrayOutputStream responseBodyOut = new ByteArrayOutputStream();
    private final AtomicInteger responseStatus = new AtomicInteger(-1);

    private SessionManager sessionManager;
    private ApiRouter router;

    @BeforeEach
    void setUp() throws Exception {
        Settings settings = new Settings(rootDir, 8080, "admin", "secret", PANIC_SETTINGS);
        sessionManager = new SessionManager();
        PanicMonitor panicMonitor = new PanicMonitor(PANIC_SETTINGS, sessionManager, () -> {});
        router = new ApiRouter(settings, sessionManager, panicMonitor, () -> {});

        requestHeaders.set("User-Agent", UA);
        InetSocketAddress addr = new InetSocketAddress(InetAddress.getByName(IP), 0);
        when(exchange.getRemoteAddress()).thenReturn(addr);
        when(exchange.getRequestHeaders()).thenReturn(requestHeaders);
        when(exchange.getResponseHeaders()).thenReturn(responseHeaders);
        when(exchange.getResponseBody()).thenReturn(responseBodyOut);
        doAnswer(inv -> {
            responseStatus.set(inv.getArgument(0));
            return null;
        }).when(exchange).sendResponseHeaders(anyInt(), anyLong());
    }

    private void setupRequest(String method, String path) {
        when(exchange.getRequestMethod()).thenReturn(method);
        when(exchange.getRequestURI()).thenReturn(URI.create(path));
    }

    private void setRequestBody(String json) {
        when(exchange.getRequestBody())
                .thenReturn(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
    }

    private void authenticateSession() {
        String sessionId = sessionManager.createSession("admin", IP, UA);
        requestHeaders.set("Cookie", "PSSESSION=" + sessionId);
    }

    // ── Public endpoints (no authentication required) ─────────────────────────

    @Test
    void handle_routesGetSession_withoutAuthentication() throws IOException {
        // Arrange
        setupRequest("GET", "/api/session");

        // Act
        router.handle(exchange);

        // Assert — session endpoint is public and always returns 200
        assertThat(responseStatus.get()).isEqualTo(200);
    }

    @Test
    void handle_routesPostLogin_withoutAuthentication() throws IOException {
        // Arrange — wrong credentials to verify the route reaches the auth handler
        setupRequest("POST", "/api/login");
        setRequestBody("{\"username\":\"admin\",\"password\":\"wrong\"}");

        // Act
        router.handle(exchange);

        // Assert — 401 from auth handler (not the ApiRouter's own auth guard)
        assertThat(responseStatus.get()).isEqualTo(401);
    }

    @Test
    void handle_routesPostLogout_withoutAuthentication() throws IOException {
        // Arrange
        setupRequest("POST", "/api/logout");

        // Act
        router.handle(exchange);

        // Assert — logout is always a no-op when there is no session
        assertThat(responseStatus.get()).isEqualTo(200);
    }

    // ── Auth guard: unauthenticated access to protected endpoints ─────────────

    @Test
    void handle_returns401ForAlbumsWithoutCookie() throws IOException {
        // Arrange
        setupRequest("GET", "/api/albums");

        // Act
        router.handle(exchange);

        // Assert
        assertThat(responseStatus.get()).isEqualTo(401);
    }

    @Test
    void handle_returns401ForAlbumsWithFakeSessionCookie() throws IOException {
        // Arrange — attacker sends a fabricated session ID
        setupRequest("GET", "/api/albums");
        requestHeaders.set("Cookie", "PSSESSION=not-a-real-session-id");

        // Act
        router.handle(exchange);

        // Assert
        assertThat(responseStatus.get()).isEqualTo(401);
    }

    @Test
    void handle_returns401ForPicturesWithoutAuth() throws IOException {
        // Arrange
        setupRequest("GET", "/api/pictures/photo.jpg");

        // Act
        router.handle(exchange);

        // Assert
        assertThat(responseStatus.get()).isEqualTo(401);
    }

    @Test
    void handle_returns401ForDeletePictureWithoutAuth() throws IOException {
        // Arrange
        setupRequest("DELETE", "/api/pictures/photo.jpg");

        // Act
        router.handle(exchange);

        // Assert
        assertThat(responseStatus.get()).isEqualTo(401);
    }

    @Test
    void handle_returns401ForImagesWithoutAuth() throws IOException {
        // Arrange
        setupRequest("GET", "/api/images/photo.jpg");

        // Act
        router.handle(exchange);

        // Assert
        assertThat(responseStatus.get()).isEqualTo(401);
    }

    @Test
    void handle_returns401ForShutdownWithoutAuth() throws IOException {
        // Arrange
        setupRequest("POST", "/api/shutdown");

        // Act
        router.handle(exchange);

        // Assert
        assertThat(responseStatus.get()).isEqualTo(401);
    }

    // ── Authenticated access ──────────────────────────────────────────────────

    @Test
    void handle_routesToAlbumHandlerWhenAuthenticated() throws IOException {
        // Arrange
        setupRequest("GET", "/api/albums");
        authenticateSession();

        // Act
        router.handle(exchange);

        // Assert — request passes the auth guard and album handler returns 200
        assertThat(responseStatus.get()).isEqualTo(200);
    }

    @Test
    void handle_returns404ForUnknownPathWhenAuthenticated() throws IOException {
        // Arrange
        setupRequest("GET", "/api/unknown/endpoint");
        authenticateSession();

        // Act
        router.handle(exchange);

        // Assert
        assertThat(responseStatus.get()).isEqualTo(404);
    }

    @Test
    void handle_sessionBoundToSourceIpAndUserAgent() throws IOException {
        // A session created from one IP must not be accepted from a different IP
        // Arrange — session belongs to a different IP and User-Agent
        String sessionId = sessionManager.createSession("admin", "10.0.0.99", "OriginalBrowser/1.0");
        requestHeaders.set("Cookie", "PSSESSION=" + sessionId);
        setupRequest("GET", "/api/albums");
        // exchange is configured with IP=127.0.0.1, UA=TestAgent/1.0 (different from session origin)

        // Act
        router.handle(exchange);

        // Assert
        assertThat(responseStatus.get()).isEqualTo(401);
    }
}
