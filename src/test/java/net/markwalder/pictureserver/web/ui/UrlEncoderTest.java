package net.markwalder.pictureserver.web.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.Test;

class UrlEncoderTest {

    @Test
    void returnsNullForNullInput() {
        assertNull(UrlEncoder.encodePath(null));
    }

    @Test
    void returnsEmptyStringForEmptyInput() {
        assertEquals("", UrlEncoder.encodePath(""));
    }

    @Test
    void leavesSimplePathUnchanged() {
        assertEquals("/albums/photo.jpg", UrlEncoder.encodePath("/albums/photo.jpg"));
    }

    @Test
    void encodesBracketsInFilename() {
        // Filenames like "['30', 0]-file.png" contain [ and ] which are invalid in URIs
        assertEquals(
            "/%5B%2730%27%2C%200%5D-file.png",
            UrlEncoder.encodePath("/['30', 0]-file.png")
        );
    }

    @Test
    void encodesSpecialCharsInRealWorldFilename() {
        // The exact filename that triggered the bug report
        String input = "/0013-IMG-40-6.0-dpmpp_sde_gpu-normal-['30', 0]-['30', 1]-3.png";
        String expected = "/0013-IMG-40-6.0-dpmpp_sde_gpu-normal-%5B%2730%27%2C%200%5D-%5B%2730%27%2C%201%5D-3.png";
        assertEquals(expected, UrlEncoder.encodePath(input));
    }

    @Test
    void encodesSpacesAsPercentTwenty() {
        assertEquals("/my%20album/my%20photo.jpg", UrlEncoder.encodePath("/my album/my photo.jpg"));
    }

    @Test
    void encodesSingleQuotes() {
        assertEquals("/it%27s-a-photo.jpg", UrlEncoder.encodePath("/it's-a-photo.jpg"));
    }

    @Test
    void preservesSlashSeparators() {
        assertEquals("/a/b/c.png", UrlEncoder.encodePath("/a/b/c.png"));
    }

    @Test
    void preservesHtmlSuffix() {
        // .html suffix appended before encoding must survive intact
        assertEquals(
            "/0013-IMG-%5B%2730%27%2C%200%5D-3.png.html",
            UrlEncoder.encodePath("/0013-IMG-['30', 0]-3.png.html")
        );
    }

    @Test
    void handlesRootPath() {
        assertEquals("/", UrlEncoder.encodePath("/"));
    }

    @Test
    void handlesPathWithoutLeadingSlash() {
        assertEquals("album/name/photo.jpg", UrlEncoder.encodePath("album/name/photo.jpg"));
    }
}
