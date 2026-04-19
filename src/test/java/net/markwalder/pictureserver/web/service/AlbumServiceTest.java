package net.markwalder.pictureserver.web.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AlbumServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void listsAlbumsAndPicturesAlphabetically() throws IOException {
        Files.createDirectory(tempDir.resolve("Zebra"));
        Files.createDirectory(tempDir.resolve("Apple"));
        Files.createFile(tempDir.resolve("photo_b.jpg"));
        Files.createFile(tempDir.resolve("photo_a.jpg"));

        AlbumService.AlbumInfo info = AlbumService.listAlbum(tempDir);

        assertEquals(2, info.albums().size());
        assertEquals("Apple", info.albums().get(0));
        assertEquals("Zebra", info.albums().get(1));
        assertEquals(2, info.pictures().size());
        assertEquals("photo_a.jpg", info.pictures().get(0));
        assertEquals("photo_b.jpg", info.pictures().get(1));
    }

    @Test
    void albumPreviewIsFirstImageAlphabetically() throws IOException {
        Path albumDir = Files.createDirectory(tempDir.resolve("Vacation"));
        Files.createFile(albumDir.resolve("z_last.jpg"));
        Files.createFile(albumDir.resolve("a_first.jpg"));

        AlbumService.AlbumInfo info = AlbumService.listAlbum(tempDir);

        assertEquals("a_first.jpg", info.albumFirstImages().get("Vacation"));
    }

    @Test
    void albumWithNoImagesHasNoPreview() throws IOException {
        Files.createDirectory(tempDir.resolve("EmptyAlbum"));

        AlbumService.AlbumInfo info = AlbumService.listAlbum(tempDir);

        assertFalse(info.albumFirstImages().containsKey("EmptyAlbum"));
    }

    @Test
    void ignoredFolderNamesAreExcluded() throws IOException {
        Files.createDirectory(tempDir.resolve("Photos Library.photoslibrary"));
        Files.createDirectory(tempDir.resolve("Photo Booth Library"));
        Files.createDirectory(tempDir.resolve("NormalAlbum"));

        AlbumService.AlbumInfo info = AlbumService.listAlbum(tempDir);

        assertEquals(1, info.albums().size());
        assertEquals("NormalAlbum", info.albums().get(0));
    }

    @Test
    void emptyDirectoryReturnsEmptyLists() throws IOException {
        AlbumService.AlbumInfo info = AlbumService.listAlbum(tempDir);

        assertTrue(info.albums().isEmpty());
        assertTrue(info.pictures().isEmpty());
        assertTrue(info.albumFirstImages().isEmpty());
    }

    @Test
    void nonImageFilesAreNotIncluded() throws IOException {
        Files.createFile(tempDir.resolve("document.pdf"));
        Files.createFile(tempDir.resolve("photo.jpg"));
        Files.createFile(tempDir.resolve("readme.txt"));

        AlbumService.AlbumInfo info = AlbumService.listAlbum(tempDir);

        assertEquals(1, info.pictures().size());
        assertEquals("photo.jpg", info.pictures().get(0));
    }

    @Test
    void allSupportedImageExtensionsAreIncluded() throws IOException {
        for (String ext : new String[]{"jpg", "jpeg", "png", "gif", "webp", "bmp"}) {
            Files.createFile(tempDir.resolve("image." + ext));
        }

        AlbumService.AlbumInfo info = AlbumService.listAlbum(tempDir);

        assertEquals(6, info.pictures().size());
    }
}
