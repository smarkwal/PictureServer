package net.markwalder.pictureserver.web.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PictureServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void returnsSiblingsSortedAlphabetically() throws IOException {
        // Arrange
        Files.createFile(tempDir.resolve("z_last.jpg"));
        Files.createFile(tempDir.resolve("a_first.jpg"));
        Files.createFile(tempDir.resolve("m_middle.jpg"));
        Path target = tempDir.resolve("m_middle.jpg");

        // Act
        PictureService.PictureInfo info = PictureService.getPictureInfo(target);

        // Assert
        assertThat(info.siblingNames()).containsExactly("a_first.jpg", "m_middle.jpg", "z_last.jpg");
    }

    @Test
    void singlePictureReturnsSingleSibling() throws IOException {
        // Arrange
        Files.createFile(tempDir.resolve("only.jpg"));
        Path target = tempDir.resolve("only.jpg");

        // Act
        PictureService.PictureInfo info = PictureService.getPictureInfo(target);

        // Assert
        assertThat(info.siblingNames()).containsExactly("only.jpg");
    }

    @Test
    void nonImageFilesAreExcludedFromSiblings() throws IOException {
        // Arrange
        Files.createFile(tempDir.resolve("photo.jpg"));
        Files.createFile(tempDir.resolve("document.pdf"));
        Path target = tempDir.resolve("photo.jpg");

        // Act
        PictureService.PictureInfo info = PictureService.getPictureInfo(target);

        // Assert
        assertThat(info.siblingNames()).containsExactly("photo.jpg");
    }

    @Test
    void pictureWithNoParentReturnsEmptySiblings() throws IOException {
        // Arrange
        Path rootPath = Path.of("/");
        Path fakePicture = rootPath.resolve("photo.jpg");

        // Act
        PictureService.PictureInfo info = PictureService.getPictureInfo(fakePicture);

        // Assert
        assertThat(info.siblingNames()).isEmpty();
    }
}
