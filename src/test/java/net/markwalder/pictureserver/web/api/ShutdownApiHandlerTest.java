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
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ShutdownApiHandlerTest {

    @Mock
    HttpExchange exchange;

    private final Headers responseHeaders = new Headers();
    private final AtomicInteger responseStatus = new AtomicInteger(-1);
    private ByteArrayOutputStream responseBodyOut;

    @BeforeEach
    void setUp() throws IOException {
        responseBodyOut = new ByteArrayOutputStream();
        when(exchange.getResponseHeaders()).thenReturn(responseHeaders);
        when(exchange.getResponseBody()).thenReturn(responseBodyOut);
        doAnswer(inv -> {
            responseStatus.set(inv.getArgument(0));
            return null;
        }).when(exchange).sendResponseHeaders(anyInt(), anyLong());
    }

    @Test
    void handle_rejectsNonPostMethod() throws IOException {
        // Arrange
        ShutdownApiHandler handler = new ShutdownApiHandler(() -> {});
        when(exchange.getRequestMethod()).thenReturn("GET");

        // Act
        handler.handle(exchange);

        // Assert
        assertThat(responseStatus.get()).isEqualTo(405);
    }

    @Test
    void handle_runsShutdownActionViaInjectedExecutor() throws IOException, InterruptedException {
        // Arrange
        CountDownLatch shutdownLatch = new CountDownLatch(1);
        Executor testExecutor = Runnable::run;
        ShutdownApiHandler handler = new ShutdownApiHandler(shutdownLatch::countDown, testExecutor);
        when(exchange.getRequestMethod()).thenReturn("POST");

        // Act
        handler.handle(exchange);

        // Assert
        String body = responseBodyOut.toString(StandardCharsets.UTF_8);
        assertThat(responseStatus.get()).isEqualTo(200);
        assertThat(body).contains("\"success\":true");
        assertThat(shutdownLatch.await(1, TimeUnit.SECONDS)).isTrue();
    }
}
