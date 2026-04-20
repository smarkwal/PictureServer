package net.markwalder.pictureserver.web;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class WebPathsTest {

    @Test
    void encodeWebPathReturnsRootForBlankPath() {
        // Act & Assert
        assertEquals("/", WebPaths.encodeWebPath(""));
        assertEquals("/", WebPaths.encodeWebPath("/"));
    }

    @Test
    void encodeWebPathEncodesSpecialCharactersPerSegment() {
        // Arrange
        String path = "/0013-IMG-6.0-dpmpp_gpu-[30, 2]-'40'-full.webp";

        // Act
        String encoded = WebPaths.encodeWebPath(path);

        // Assert
        assertEquals("/0013-IMG-6.0-dpmpp_gpu-%5B30%2C%202%5D-%2740%27-full.webp", encoded);
    }

    @Test
    void encodeWebPathAvoidsDoubleEncoding() {
        // Arrange
        String alreadyEncoded = "/Cities/New%20York";

        // Act
        String encoded = WebPaths.encodeWebPath(alreadyEncoded);

        // Assert
        assertEquals("/Cities/New%20York", encoded);
    }
}
