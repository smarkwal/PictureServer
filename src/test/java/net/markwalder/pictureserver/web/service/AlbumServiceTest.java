package net.markwalder.pictureserver.web.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AlbumServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void listsAlbumsAndPicturesAlphabetically() throws IOException {
        // Arrange
        Files.createDirectory(tempDir.resolve("Zebra"));
        Files.createDirectory(tempDir.resolve("Apple"));
        Files.createFile(tempDir.resolve("photo_b.jpg"));
        Files.createFile(tempDir.resolve("photo_a.jpg"));

        // Act
        AlbumService.AlbumInfo info = AlbumService.listAlbum(tempDir);

        // Assert
        assertThat(info.albums()).containsExactly("Apple", "Zebra");
        assertThat(info.pictures()).containsExactly("photo_a.jpg", "photo_b.jpg");
    }

    @Test
    void albumPreviewIsFirstImageAlphabetically() throws IOException {
        // Arrange
        Path albumDir = Files.createDirectory(tempDir.resolve("Vacation"));
        Files.createFile(albumDir.resolve("z_last.jpg"));
        Files.createFile(albumDir.resolve("a_first.jpg"));

        // Act
        AlbumService.AlbumInfo info = AlbumService.listAlbum(tempDir);

        // Assert
        assertThat(info.albumFirstImages()).containsEntry("Vacation", "a_first.jpg");
    }

    @Test
    void albumWithNoImagesHasNoPreview() throws IOException {
        // Arrange
        Files.createDirectory(tempDir.resolve("EmptyAlbum"));

        // Act
        AlbumService.AlbumInfo info = AlbumService.listAlbum(tempDir);

        // Assert
        assertThat(info.albumFirstImages()).doesNotContainKey("EmptyAlbum");
    }

    @Test
    void ignoredFolderNamesAreExcluded() throws IOException {
        // Arrange
        Files.createDirectory(tempDir.resolve("Photos Library.photoslibrary"));
        Files.createDirectory(tempDir.resolve("Photo Booth Library"));
        Files.createDirectory(tempDir.resolve("NormalAlbum"));

        // Act
        AlbumService.AlbumInfo info = AlbumService.listAlbum(tempDir);

        // Assert
        assertThat(info.albums()).containsExactly("NormalAlbum");
    }

    @Test
    void emptyDirectoryReturnsEmptyLists() throws IOException {
        // Act
        AlbumService.AlbumInfo info = AlbumService.listAlbum(tempDir);

        // Assert
        assertThat(info.albums()).isEmpty();
        assertThat(info.pictures()).isEmpty();
        assertThat(info.albumFirstImages()).isEmpty();
    }

    @Test
    void nonImageFilesAreNotIncluded() throws IOException {
        // Arrange
        Files.createFile(tempDir.resolve("document.pdf"));
        Files.createFile(tempDir.resolve("photo.jpg"));
        Files.createFile(tempDir.resolve("readme.txt"));

        // Act
        AlbumService.AlbumInfo info = AlbumService.listAlbum(tempDir);

        // Assert
        assertThat(info.pictures()).containsExactly("photo.jpg");
    }

    @Test
    void allSupportedImageExtensionsAreIncluded() throws IOException {
        // Arrange
        for (String ext : new String[]{"jpg", "jpeg", "png", "gif", "webp", "bmp"}) {
            Files.createFile(tempDir.resolve("image." + ext));
        }

        // Act
        AlbumService.AlbumInfo info = AlbumService.listAlbum(tempDir);

        // Assert
        assertThat(info.pictures()).hasSize(6);
    }
}
