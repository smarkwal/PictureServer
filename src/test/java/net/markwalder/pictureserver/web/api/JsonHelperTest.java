package net.markwalder.pictureserver.web.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JsonHelperTest {

    private record SampleBean(String name, int count) {
        @JsonCreator
        SampleBean(
                @JsonProperty(value = "name", required = true) String name,
                @JsonProperty(value = "count", required = true) int count) {
            this.name = name;
            this.count = count;
        }
    }

    private enum Color { RED, GREEN, BLUE }

    private record ColorBean(Color color) {
        @JsonCreator
        ColorBean(@JsonProperty(value = "color", required = true) Color color) {
            this.color = color;
        }
    }

    @Mock
    HttpExchange exchange;

    // ── MAPPER configuration tests ────────────────────────────────────────────

    @Test
    void mapper_deserializesValidJson() throws IOException {
        // Arrange
        byte[] json = "{\"name\":\"test\",\"count\":42}".getBytes(StandardCharsets.UTF_8);

        // Act
        SampleBean result = JsonHelper.MAPPER.readValue(json, SampleBean.class);

        // Assert
        assertThat(result.name()).isEqualTo("test");
        assertThat(result.count()).isEqualTo(42);
    }

    @Test
    void mapper_rejectsUnknownProperties() {
        // Arrange
        byte[] json = "{\"name\":\"test\",\"count\":1,\"extra\":\"value\"}".getBytes(StandardCharsets.UTF_8);

        // Act & Assert
        assertThatThrownBy(() -> JsonHelper.MAPPER.readValue(json, SampleBean.class))
                .isInstanceOf(JsonProcessingException.class);
    }

    @Test
    void mapper_rejectsNullForIntPrimitive() {
        // Arrange
        byte[] json = "{\"name\":\"test\",\"count\":null}".getBytes(StandardCharsets.UTF_8);

        // Act & Assert
        assertThatThrownBy(() -> JsonHelper.MAPPER.readValue(json, SampleBean.class))
                .isInstanceOf(JsonProcessingException.class);
    }

    @Test
    void mapper_rejectsStringCoercedToInt() {
        // Arrange — ALLOW_COERCION_OF_SCALARS is disabled; "42" must not become 42
        byte[] json = "{\"name\":\"test\",\"count\":\"42\"}".getBytes(StandardCharsets.UTF_8);

        // Act & Assert
        assertThatThrownBy(() -> JsonHelper.MAPPER.readValue(json, SampleBean.class))
                .isInstanceOf(JsonProcessingException.class);
    }

    @Test
    void mapper_rejectsNumericIndexForEnum() {
        // Arrange — FAIL_ON_NUMBERS_FOR_ENUMS is enabled; ordinal 0 must not become RED
        byte[] json = "{\"color\":0}".getBytes(StandardCharsets.UTF_8);

        // Act & Assert
        assertThatThrownBy(() -> JsonHelper.MAPPER.readValue(json, ColorBean.class))
                .isInstanceOf(JsonProcessingException.class);
    }

    // ── sendJson / readJson HTTP helper tests ─────────────────────────────────

    private Headers responseHeaders;
    private ByteArrayOutputStream responseBodyOut;
    private AtomicInteger responseStatus;

    private void setupResponseCapture() throws IOException {
        responseHeaders = new Headers();
        responseBodyOut = new ByteArrayOutputStream();
        responseStatus = new AtomicInteger(-1);
        when(exchange.getResponseHeaders()).thenReturn(responseHeaders);
        when(exchange.getResponseBody()).thenReturn(responseBodyOut);
        doAnswer(inv -> {
            responseStatus.set(inv.getArgument(0));
            return null;
        }).when(exchange).sendResponseHeaders(anyInt(), anyLong());
    }

    @Test
    void sendJson_setsContentTypeHeader() throws IOException {
        // Arrange
        setupResponseCapture();

        // Act
        JsonHelper.sendJson(exchange, 200, Map.of("ok", true));

        // Assert
        assertThat(responseHeaders.getFirst("Content-Type"))
                .isEqualTo("application/json; charset=utf-8");
    }

    @Test
    void sendJson_sendsCorrectStatusCode() throws IOException {
        // Arrange
        setupResponseCapture();

        // Act
        JsonHelper.sendJson(exchange, 201, Map.of());

        // Assert
        assertThat(responseStatus.get()).isEqualTo(201);
    }

    @Test
    void sendJson_writesSerializedJsonBody() throws IOException {
        // Arrange
        setupResponseCapture();

        // Act
        JsonHelper.sendJson(exchange, 200, Map.of("success", true));

        // Assert
        String body = responseBodyOut.toString(StandardCharsets.UTF_8);
        assertThat(body).contains("\"success\"").contains("true");
    }

    @Test
    void readJson_deserializesRequestBody() throws IOException {
        // Arrange
        byte[] json = "{\"name\":\"hello\",\"count\":7}".getBytes(StandardCharsets.UTF_8);
        when(exchange.getRequestBody()).thenReturn(new ByteArrayInputStream(json));

        // Act
        SampleBean result = JsonHelper.readJson(exchange, SampleBean.class);

        // Assert
        assertThat(result.name()).isEqualTo("hello");
        assertThat(result.count()).isEqualTo(7);
    }
}
