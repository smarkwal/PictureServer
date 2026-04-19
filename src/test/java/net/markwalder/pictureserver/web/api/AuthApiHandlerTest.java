package net.markwalder.pictureserver.web.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import net.markwalder.pictureserver.auth.SessionManager;
import net.markwalder.pictureserver.config.Settings;
import net.markwalder.pictureserver.config.Settings.PanicSettings;
import net.markwalder.pictureserver.security.PanicMonitor;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthApiHandlerTest {

    private static final String USERNAME = "admin";
    private static final String PASSWORD = "s3cret";
    private static final String IP = "127.0.0.1";
    private static final String UA = "TestAgent/1.0";

    @Mock
    HttpExchange exchange;

    private final Headers requestHeaders = new Headers();
    private final Headers responseHeaders = new Headers();
    private final ByteArrayOutputStream responseBodyOut = new ByteArrayOutputStream();
    private final AtomicInteger responseStatus = new AtomicInteger(-1);

    private SessionManager sessionManager;
    private AuthApiHandler handler;

    @BeforeEach
    void setUp() throws Exception {
        Settings settings = new Settings(
                Path.of("/tmp"), 8080, USERNAME, PASSWORD,
                new PanicSettings(true, true, true, 5, 60, 5, 60, 10, 60, 5, 60));
        sessionManager = new SessionManager();
        PanicMonitor panicMonitor = new PanicMonitor(settings.panic(), sessionManager, () -> {});
        handler = new AuthApiHandler(settings, sessionManager, panicMonitor);

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

    private void setRequestBody(String json) {
        when(exchange.getRequestBody())
                .thenReturn(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));
    }

    // ── handleLogin ───────────────────────────────────────────────────────────

    @Test
    void handleLogin_rejectsGetMethod() throws IOException {
        // Arrange
        when(exchange.getRequestMethod()).thenReturn("GET");

        // Act
        handler.handleLogin(exchange);

        // Assert
        assertThat(responseStatus.get()).isEqualTo(405);
    }

    @Test
    void handleLogin_rejectsMalformedJson() throws IOException {
        // Arrange
        when(exchange.getRequestMethod()).thenReturn("POST");
        setRequestBody("{not valid json}");

        // Act
        handler.handleLogin(exchange);

        // Assert
        assertThat(responseStatus.get()).isEqualTo(400);
    }

    @Test
    void handleLogin_rejectsMissingRequiredField() throws IOException {
        // Arrange — password field is absent; @JsonProperty(required=true) must cause rejection
        when(exchange.getRequestMethod()).thenReturn("POST");
        setRequestBody("{\"username\":\"" + USERNAME + "\"}");

        // Act
        handler.handleLogin(exchange);

        // Assert
        assertThat(responseStatus.get()).isEqualTo(400);
    }

    @Test
    void handleLogin_rejectsUnknownJsonField() throws IOException {
        // Arrange — extra field triggers FAIL_ON_UNKNOWN_PROPERTIES
        when(exchange.getRequestMethod()).thenReturn("POST");
        setRequestBody("{\"username\":\"" + USERNAME + "\",\"password\":\"" + PASSWORD + "\",\"token\":\"x\"}");

        // Act
        handler.handleLogin(exchange);

        // Assert
        assertThat(responseStatus.get()).isEqualTo(400);
    }

    @Test
    void handleLogin_rejectsWrongPassword() throws IOException {
        // Arrange
        when(exchange.getRequestMethod()).thenReturn("POST");
        setRequestBody("{\"username\":\"" + USERNAME + "\",\"password\":\"wrongpass\"}");

        // Act
        handler.handleLogin(exchange);

        // Assert
        assertThat(responseStatus.get()).isEqualTo(401);
    }

    @Test
    void handleLogin_rejectsWrongUsername() throws IOException {
        // Arrange
        when(exchange.getRequestMethod()).thenReturn("POST");
        setRequestBody("{\"username\":\"attacker\",\"password\":\"" + PASSWORD + "\"}");

        // Act
        handler.handleLogin(exchange);

        // Assert
        assertThat(responseStatus.get()).isEqualTo(401);
    }

    @Test
    void handleLogin_acceptsCorrectCredentials() throws IOException {
        // Arrange
        when(exchange.getRequestMethod()).thenReturn("POST");
        setRequestBody("{\"username\":\"" + USERNAME + "\",\"password\":\"" + PASSWORD + "\"}");

        // Act
        handler.handleLogin(exchange);

        // Assert
        assertThat(responseStatus.get()).isEqualTo(200);
    }

    @Test
    void handleLogin_setsPSSESSIONCookieOnSuccess() throws IOException {
        // Arrange
        when(exchange.getRequestMethod()).thenReturn("POST");
        setRequestBody("{\"username\":\"" + USERNAME + "\",\"password\":\"" + PASSWORD + "\"}");

        // Act
        handler.handleLogin(exchange);

        // Assert
        String setCookie = responseHeaders.getFirst("Set-Cookie");
        assertThat(setCookie).startsWith("PSSESSION=");
        assertThat(setCookie).contains("HttpOnly");
        assertThat(setCookie).contains("SameSite=Strict");
    }

    @Test
    void handleLogin_createsAuthenticatedSessionOnSuccess() throws IOException {
        // Arrange
        when(exchange.getRequestMethod()).thenReturn("POST");
        setRequestBody("{\"username\":\"" + USERNAME + "\",\"password\":\"" + PASSWORD + "\"}");

        // Act
        handler.handleLogin(exchange);

        // Assert — session ID from Set-Cookie header must be valid in SessionManager
        String setCookie = responseHeaders.getFirst("Set-Cookie");
        String sessionId = setCookie.substring("PSSESSION=".length(), setCookie.indexOf(";"));
        assertThat(sessionManager.isAuthenticated(sessionId, IP, UA)).isTrue();
    }

    // ── handleLogout ──────────────────────────────────────────────────────────

    @Test
    void handleLogout_rejectsGetMethod() throws IOException {
        // Arrange
        when(exchange.getRequestMethod()).thenReturn("GET");

        // Act
        handler.handleLogout(exchange);

        // Assert
        assertThat(responseStatus.get()).isEqualTo(405);
    }

    @Test
    void handleLogout_removesSessionFromManager() throws IOException {
        // Arrange
        String sessionId = sessionManager.createSession(USERNAME, IP, UA);
        requestHeaders.set("Cookie", "PSSESSION=" + sessionId);
        when(exchange.getRequestMethod()).thenReturn("POST");

        // Act
        handler.handleLogout(exchange);

        // Assert
        assertThat(sessionManager.isAuthenticated(sessionId, IP, UA)).isFalse();
    }

    @Test
    void handleLogout_clearsCookieWithMaxAgeZero() throws IOException {
        // Arrange
        when(exchange.getRequestMethod()).thenReturn("POST");

        // Act
        handler.handleLogout(exchange);

        // Assert
        String setCookie = responseHeaders.getFirst("Set-Cookie");
        assertThat(setCookie).contains("PSSESSION=;");
        assertThat(setCookie).contains("Max-Age=0");
    }

    @Test
    void handleLogout_succeedsWithoutExistingSession() throws IOException {
        // Arrange — no cookie present, no active session
        when(exchange.getRequestMethod()).thenReturn("POST");

        // Act
        handler.handleLogout(exchange);

        // Assert — graceful no-op, not an error
        assertThat(responseStatus.get()).isEqualTo(200);
    }
}
