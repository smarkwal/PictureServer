package net.markwalder.pictureserver.web.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class HttpHelperTest {

    @Mock
    HttpExchange exchange;

    private final Headers requestHeaders = new Headers();

    @BeforeEach
    void setUp() {
        when(exchange.getRequestHeaders()).thenReturn(requestHeaders);
    }

    @Test
    void getSourceIp_returnsRemoteAddressIp() throws Exception {
        // Arrange
        InetSocketAddress remoteAddress = new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 12345);
        when(exchange.getRemoteAddress()).thenReturn(remoteAddress);

        // Act
        String sourceIp = HttpHelper.getSourceIp(exchange);

        // Assert
        assertThat(sourceIp).isEqualTo("127.0.0.1");
    }

    @Test
    void getUserAgent_returnsHeaderWhenPresent() {
        // Arrange
        requestHeaders.set("User-Agent", "TestBrowser/1.0");

        // Act
        String userAgent = HttpHelper.getUserAgent(exchange);

        // Assert
        assertThat(userAgent).isEqualTo("TestBrowser/1.0");
    }

    @Test
    void getUserAgent_returnsUnknownWhenMissing() {
        // Act
        String userAgent = HttpHelper.getUserAgent(exchange);

        // Assert
        assertThat(userAgent).isEqualTo("unknown");
    }

    @Test
    void readCookie_returnsCookieValueWhenPresent() {
        // Arrange
        requestHeaders.set("Cookie", "PSSESSION=abc123; theme=light");

        // Act
        String sessionId = HttpHelper.readCookie(exchange, "PSSESSION").orElse(null);

        // Assert
        assertThat(sessionId).isEqualTo("abc123");
    }

    @Test
    void readCookie_ignoresMalformedCookieParts() {
        // Arrange
        requestHeaders.set("Cookie", "invalid-cookie; another=ok");

        // Act
        String cookie = HttpHelper.readCookie(exchange, "invalid-cookie").orElse(null);

        // Assert
        assertThat(cookie).isNull();
    }

    @Test
    void readCookie_returnsEmptyWhenMissing() {
        // Arrange
        requestHeaders.set("Cookie", "theme=dark");

        // Act
        boolean present = HttpHelper.readCookie(exchange, "PSSESSION").isPresent();

        // Assert
        assertThat(present).isFalse();
    }
}
