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
import java.util.concurrent.atomic.AtomicInteger;
import net.markwalder.pictureserver.auth.SessionManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SessionApiHandlerTest {

    private static final String IP = "127.0.0.1";
    private static final String UA = "TestAgent/1.0";

    @Mock
    HttpExchange exchange;

    private final Headers requestHeaders = new Headers();
    private final Headers responseHeaders = new Headers();
    private final AtomicInteger responseStatus = new AtomicInteger(-1);
    private ByteArrayOutputStream responseBodyOut;

    private SessionManager sessionManager;
    private SessionApiHandler handler;

    @BeforeEach
    void setUp() throws Exception {
        sessionManager = new SessionManager();
        handler = new SessionApiHandler(sessionManager);

        responseBodyOut = new ByteArrayOutputStream();
        requestHeaders.set("User-Agent", UA);
        InetSocketAddress remoteAddress = new InetSocketAddress(InetAddress.getByName(IP), 0);

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
        handler.handle(exchange);

        // Assert
        assertThat(responseStatus.get()).isEqualTo(405);
    }

    @Test
    void handle_returnsAuthenticatedFalseWithoutCookie() throws IOException {
        // Arrange
        when(exchange.getRequestMethod()).thenReturn("GET");

        // Act
        handler.handle(exchange);

        // Assert
        String body = responseBodyOut.toString(StandardCharsets.UTF_8);
        assertThat(responseStatus.get()).isEqualTo(200);
        assertThat(body).contains("\"authenticated\":false");
    }

    @Test
    void handle_returnsAuthenticatedTrueForValidSession() throws IOException {
        // Arrange
        String sessionId = sessionManager.createSession("admin", IP, UA);
        requestHeaders.set("Cookie", "PSSESSION=" + sessionId);
        when(exchange.getRequestMethod()).thenReturn("GET");

        // Act
        handler.handle(exchange);

        // Assert
        String body = responseBodyOut.toString(StandardCharsets.UTF_8);
        assertThat(responseStatus.get()).isEqualTo(200);
        assertThat(body).contains("\"authenticated\":true");
    }

    @Test
    void handle_returnsAuthenticatedFalseForInvalidSession() throws IOException {
        // Arrange
        requestHeaders.set("Cookie", "PSSESSION=invalid-session");
        when(exchange.getRequestMethod()).thenReturn("GET");

        // Act
        handler.handle(exchange);

        // Assert
        String body = responseBodyOut.toString(StandardCharsets.UTF_8);
        assertThat(responseStatus.get()).isEqualTo(200);
        assertThat(body).contains("\"authenticated\":false");
    }
}
