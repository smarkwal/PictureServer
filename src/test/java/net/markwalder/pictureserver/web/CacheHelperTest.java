package net.markwalder.pictureserver.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CacheHelperTest {

    @Mock
    HttpExchange exchange;

    private final Headers requestHeaders = new Headers();

    @BeforeEach
    void setUp() {
        when(exchange.getRequestHeaders()).thenReturn(requestHeaders);
    }

    // ── buildETag ──────────────────────────────────────────────────────────────

    @Test
    void buildETag_formatsAsQuotedHex() {
        // Act & Assert
        assertThat(CacheHelper.buildETag(255, 4096)).isEqualTo("\"ff-1000\"");
    }

    @Test
    void buildETag_handlesZeroValues() {
        // Act & Assert
        assertThat(CacheHelper.buildETag(0, 0)).isEqualTo("\"0-0\"");
    }

    // ── formatHttpDate ─────────────────────────────────────────────────────────

    @Test
    void formatHttpDate_formatsEpochAsRFC1123() {
        // Act & Assert
        assertThat(CacheHelper.formatHttpDate(0)).isEqualTo("Thu, 1 Jan 1970 00:00:00 GMT");
    }

    @Test
    void formatHttpDate_formatsKnownTimestamp() {
        // 2024-01-15T12:00:00Z
        // Act & Assert
        assertThat(CacheHelper.formatHttpDate(1705320000000L)).isEqualTo("Mon, 15 Jan 2024 12:00:00 GMT");
    }

    // ── isNotModified ──────────────────────────────────────────────────────────

    @Test
    void isNotModified_returnsFalse_whenNoConditionalHeaders() {
        // Act & Assert
        assertThat(CacheHelper.isNotModified(exchange, "\"abc\"", 1000L)).isFalse();
    }

    @Test
    void isNotModified_returnsTrue_whenIfNoneMatchEqualsETag() {
        // Arrange
        requestHeaders.set("If-None-Match", "\"abc\"");

        // Act & Assert
        assertThat(CacheHelper.isNotModified(exchange, "\"abc\"", 1000L)).isTrue();
    }

    @Test
    void isNotModified_returnsFalse_whenIfNoneMatchDiffers() {
        // Arrange
        requestHeaders.set("If-None-Match", "\"xyz\"");

        // Act & Assert
        assertThat(CacheHelper.isNotModified(exchange, "\"abc\"", 1000L)).isFalse();
    }

    @Test
    void isNotModified_returnsTrue_whenIfNoneMatchIsWildcard() {
        // Arrange
        requestHeaders.set("If-None-Match", "*");

        // Act & Assert
        assertThat(CacheHelper.isNotModified(exchange, "\"abc\"", 1000L)).isTrue();
    }

    @Test
    void isNotModified_returnsTrue_whenIfNoneMatchListContainsMatchingETag() {
        // Arrange
        requestHeaders.set("If-None-Match", "\"xyz\", \"abc\", \"def\"");

        // Act & Assert
        assertThat(CacheHelper.isNotModified(exchange, "\"abc\"", 1000L)).isTrue();
    }

    @Test
    void isNotModified_returnsTrue_whenWeakIfNoneMatchEqualsStrongETag() {
        // Arrange — weak tag W/"abc" must match strong tag "abc"
        requestHeaders.set("If-None-Match", "W/\"abc\"");

        // Act & Assert
        assertThat(CacheHelper.isNotModified(exchange, "\"abc\"", 1000L)).isTrue();
    }

    @Test
    void isNotModified_returnsTrue_whenFileNotModifiedSinceRequestTime() {
        // Arrange — request says "give me changes after 00:00:01"; file millis = 1000 (1s)
        requestHeaders.set("If-Modified-Since", "Thu, 1 Jan 1970 00:00:01 GMT");

        // Act & Assert — file time (1000ms) <= request time (1000ms) → not modified
        assertThat(CacheHelper.isNotModified(exchange, "\"abc\"", 1000L)).isTrue();
    }

    @Test
    void isNotModified_returnsFalse_whenFileModifiedAfterRequestTime() {
        // Arrange — request says "give me changes after 00:00:00"; file millis = 1000 (1s)
        requestHeaders.set("If-Modified-Since", "Thu, 1 Jan 1970 00:00:00 GMT");

        // Act & Assert — file time (1000ms) > request time (0ms) → was modified
        assertThat(CacheHelper.isNotModified(exchange, "\"abc\"", 1000L)).isFalse();
    }

    @Test
    void isNotModified_truncatesFileTimeToSeconds_forIfModifiedSinceComparison() {
        // Arrange — file has sub-second millis (1500ms = 1.5s, truncates to 1s)
        requestHeaders.set("If-Modified-Since", "Thu, 1 Jan 1970 00:00:01 GMT");

        // Act & Assert — truncated file time (1000ms) <= request time (1000ms) → not modified
        assertThat(CacheHelper.isNotModified(exchange, "\"abc\"", 1500L)).isTrue();
    }

    @Test
    void isNotModified_returnsFalse_whenIfModifiedSinceDateIsMalformed() {
        // Arrange
        requestHeaders.set("If-Modified-Since", "not-a-date");

        // Act & Assert
        assertThat(CacheHelper.isNotModified(exchange, "\"abc\"", 1000L)).isFalse();
    }

    @Test
    void isNotModified_prefersIfNoneMatchOverIfModifiedSince() {
        // Arrange — ETag doesn't match, but If-Modified-Since would say not-modified
        requestHeaders.set("If-None-Match", "\"xyz\"");
        requestHeaders.set("If-Modified-Since", "Thu, 1 Jan 1970 00:00:01 GMT");

        // Act & Assert — If-None-Match takes precedence; ETag mismatch → false
        assertThat(CacheHelper.isNotModified(exchange, "\"abc\"", 1000L)).isFalse();
    }
}
