package net.markwalder.pictureserver.web.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PictureServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void returnsSiblingsSortedAlphabetically() throws IOException {
        Files.createFile(tempDir.resolve("z_last.jpg"));
        Files.createFile(tempDir.resolve("a_first.jpg"));
        Files.createFile(tempDir.resolve("m_middle.jpg"));
        Path target = tempDir.resolve("m_middle.jpg");

        PictureService.PictureInfo info = PictureService.getPictureInfo(target);

        assertEquals(3, info.siblingNames().size());
        assertEquals("a_first.jpg", info.siblingNames().get(0));
        assertEquals("m_middle.jpg", info.siblingNames().get(1));
        assertEquals("z_last.jpg", info.siblingNames().get(2));
    }

    @Test
    void singlePictureReturnsSingleSibling() throws IOException {
        Files.createFile(tempDir.resolve("only.jpg"));
        Path target = tempDir.resolve("only.jpg");

        PictureService.PictureInfo info = PictureService.getPictureInfo(target);

        assertEquals(1, info.siblingNames().size());
        assertEquals("only.jpg", info.siblingNames().get(0));
    }

    @Test
    void nonImageFilesAreExcludedFromSiblings() throws IOException {
        Files.createFile(tempDir.resolve("photo.jpg"));
        Files.createFile(tempDir.resolve("document.pdf"));
        Path target = tempDir.resolve("photo.jpg");

        PictureService.PictureInfo info = PictureService.getPictureInfo(target);

        assertEquals(1, info.siblingNames().size());
        assertEquals("photo.jpg", info.siblingNames().get(0));
    }

    @Test
    void pictureWithNoParentReturnsEmptySiblings() throws IOException {
        Path rootPath = Path.of("/");
        Path fakePicture = rootPath.resolve("photo.jpg");

        PictureService.PictureInfo info = PictureService.getPictureInfo(fakePicture);

        assertTrue(info.siblingNames().isEmpty());
    }
}
