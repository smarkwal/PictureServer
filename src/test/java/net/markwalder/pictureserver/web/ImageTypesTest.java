package net.markwalder.pictureserver.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ImageTypesTest {

    @ParameterizedTest
    @ValueSource(strings = {"photo.jpg", "photo.jpeg", "photo.png", "photo.gif", "photo.webp", "photo.bmp"})
    void isImageFile_recognizesLowercaseExtensions(String filename) {
        // Act & Assert
        assertThat(ImageTypes.isImageFile(filename)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"photo.JPG", "photo.JPEG", "photo.PNG", "photo.GIF", "photo.WEBP", "photo.BMP"})
    void isImageFile_isCaseInsensitive(String filename) {
        // Act & Assert
        assertThat(ImageTypes.isImageFile(filename)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"document.txt", "archive.zip", "video.mp4", "script.js", "image.tiff", "photo.svg"})
    void isImageFile_returnsFalseForNonImageExtensions(String filename) {
        // Act & Assert
        assertThat(ImageTypes.isImageFile(filename)).isFalse();
    }

    @Test
    void isImageFile_returnsFalseForEmptyString() {
        // Act & Assert
        assertThat(ImageTypes.isImageFile("")).isFalse();
    }

    @Test
    void isImageFile_returnsFalseForFilenameWithoutExtension() {
        // Act & Assert
        assertThat(ImageTypes.isImageFile("photo")).isFalse();
    }

    @Test
    void mimeType_returnsImageJpegForJpg() {
        // Act & Assert
        assertThat(ImageTypes.mimeType("photo.jpg")).isEqualTo("image/jpeg");
    }

    @Test
    void mimeType_returnsImageJpegForJpeg() {
        // Act & Assert
        assertThat(ImageTypes.mimeType("photo.jpeg")).isEqualTo("image/jpeg");
    }

    @Test
    void mimeType_returnsImagePngForPng() {
        // Act & Assert
        assertThat(ImageTypes.mimeType("photo.png")).isEqualTo("image/png");
    }

    @Test
    void mimeType_returnsImageGifForGif() {
        // Act & Assert
        assertThat(ImageTypes.mimeType("photo.gif")).isEqualTo("image/gif");
    }

    @Test
    void mimeType_returnsImageWebpForWebp() {
        // Act & Assert
        assertThat(ImageTypes.mimeType("photo.webp")).isEqualTo("image/webp");
    }

    @Test
    void mimeType_returnsImageBmpForBmp() {
        // Act & Assert
        assertThat(ImageTypes.mimeType("photo.bmp")).isEqualTo("image/bmp");
    }

    @Test
    void mimeType_isCaseInsensitive() {
        // Act & Assert
        assertThat(ImageTypes.mimeType("photo.JPG")).isEqualTo("image/jpeg");
        assertThat(ImageTypes.mimeType("PHOTO.PNG")).isEqualTo("image/png");
    }

    @Test
    void mimeType_returnsOctetStreamFallbackForUnknownExtension() {
        // Act & Assert
        assertThat(ImageTypes.mimeType("document.txt")).isEqualTo("application/octet-stream");
        assertThat(ImageTypes.mimeType("photo")).isEqualTo("application/octet-stream");
        assertThat(ImageTypes.mimeType("")).isEqualTo("application/octet-stream");
    }
}
