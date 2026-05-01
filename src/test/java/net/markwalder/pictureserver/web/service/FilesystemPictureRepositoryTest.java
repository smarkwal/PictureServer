package net.markwalder.pictureserver.web.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import net.markwalder.pictureserver.config.Settings;
import net.markwalder.pictureserver.config.Settings.PanicSettings;
import net.markwalder.pictureserver.web.service.PictureRepository.AlbumInfo;
import net.markwalder.pictureserver.web.service.PictureRepository.ImageInfo;
import net.markwalder.pictureserver.web.service.PictureRepository.PictureInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FilesystemPictureRepositoryTest {

    private static final PanicSettings PANIC_SETTINGS =
            new PanicSettings(true, true, true, 5, 60, 5, 60, 10, 60, 5, 60);

    @TempDir
    Path rootDir;

    private FilesystemPictureRepository repository;

    @BeforeEach
    void setUp() {
        Settings settings = new Settings(rootDir, 8080, "admin", "secret", PANIC_SETTINGS);
        repository = new FilesystemPictureRepository(settings);
    }

    // -------------------------------------------------------------------------
    // getAlbumInfo
    // -------------------------------------------------------------------------

    @Test
    void getAlbumInfo_listsAlbumsAndPicturesAlphabetically() throws IOException {
        // Arrange
        Files.createDirectory(rootDir.resolve("Zebra"));
        Files.createDirectory(rootDir.resolve("Apple"));
        Files.createFile(rootDir.resolve("photo_b.jpg"));
        Files.createFile(rootDir.resolve("photo_a.jpg"));

        // Act
        Optional<AlbumInfo> result = repository.getAlbumInfo("/");

        // Assert
        assertThat(result).isPresent();
        assertThat(result.get().albums()).containsExactly("Apple", "Zebra");
        assertThat(result.get().pictures()).containsExactly("photo_a.jpg", "photo_b.jpg");
    }

    @Test
    void getAlbumInfo_albumPreviewIsFirstImageAlphabetically() throws IOException {
        // Arrange
        Path albumDir = Files.createDirectory(rootDir.resolve("Vacation"));
        Files.createFile(albumDir.resolve("z_last.jpg"));
        Files.createFile(albumDir.resolve("a_first.jpg"));

        // Act
        Optional<AlbumInfo> result = repository.getAlbumInfo("/");

        // Assert
        assertThat(result).isPresent();
        assertThat(result.get().albumFirstImages()).containsEntry("Vacation", "a_first.jpg");
    }

    @Test
    void getAlbumInfo_albumWithNoImagesHasNoPreview() throws IOException {
        // Arrange
        Files.createDirectory(rootDir.resolve("EmptyAlbum"));

        // Act
        Optional<AlbumInfo> result = repository.getAlbumInfo("/");

        // Assert
        assertThat(result).isPresent();
        assertThat(result.get().albumFirstImages()).doesNotContainKey("EmptyAlbum");
    }

    @Test
    void getAlbumInfo_ignoredFolderNamesAreExcluded() throws IOException {
        // Arrange
        Files.createDirectory(rootDir.resolve("Photos Library.photoslibrary"));
        Files.createDirectory(rootDir.resolve("Photo Booth Library"));
        Files.createDirectory(rootDir.resolve("NormalAlbum"));

        // Act
        Optional<AlbumInfo> result = repository.getAlbumInfo("/");

        // Assert
        assertThat(result).isPresent();
        assertThat(result.get().albums()).containsExactly("NormalAlbum");
    }

    @Test
    void getAlbumInfo_ignoresFavoritesFolder() throws IOException {
        // Arrange
        Files.createDirectory(rootDir.resolve("Favorites"));
        Files.createDirectory(rootDir.resolve("NormalAlbum"));

        // Act
        Optional<AlbumInfo> result = repository.getAlbumInfo("/");

        // Assert
        assertThat(result).isPresent();
        assertThat(result.get().albums()).containsExactly("NormalAlbum");
    }

    @Test
    void getAlbumInfo_emptyDirectoryReturnsEmptyLists() throws IOException {
        // Act & Assert
        Optional<AlbumInfo> result = repository.getAlbumInfo("/");
        assertThat(result).isPresent();
        assertThat(result.get().albums()).isEmpty();
        assertThat(result.get().pictures()).isEmpty();
        assertThat(result.get().albumFirstImages()).isEmpty();
    }

    @Test
    void getAlbumInfo_nonImageFilesAreNotIncluded() throws IOException {
        // Arrange
        Files.createFile(rootDir.resolve("document.pdf"));
        Files.createFile(rootDir.resolve("photo.jpg"));
        Files.createFile(rootDir.resolve("readme.txt"));

        // Act
        Optional<AlbumInfo> result = repository.getAlbumInfo("/");

        // Assert
        assertThat(result).isPresent();
        assertThat(result.get().pictures()).containsExactly("photo.jpg");
    }

    @Test
    void getAlbumInfo_allSupportedImageExtensionsAreIncluded() throws IOException {
        // Arrange
        for (String ext : new String[]{"jpg", "jpeg", "png", "gif", "webp", "bmp"}) {
            Files.createFile(rootDir.resolve("image." + ext));
        }

        // Act
        Optional<AlbumInfo> result = repository.getAlbumInfo("/");

        // Assert
        assertThat(result).isPresent();
        assertThat(result.get().pictures()).hasSize(6);
    }

    @Test
    void getAlbumInfo_returnsEmptyForNonExistentDirectory() throws IOException {
        // Act & Assert
        Optional<AlbumInfo> result = repository.getAlbumInfo("/missing");
        assertThat(result).isEmpty();
    }

    @Test
    void getAlbumInfo_throwsSecurityExceptionForPathTraversal() {
        // Act & Assert
        assertThatThrownBy(() -> repository.getAlbumInfo("/../etc"))
                .isInstanceOf(SecurityException.class);
    }

    // -------------------------------------------------------------------------
    // getPictureInfo
    // -------------------------------------------------------------------------

    @Test
    void getPictureInfo_returnsSiblingsSortedAlphabetically() throws IOException {
        // Arrange
        Files.createFile(rootDir.resolve("z_last.jpg"));
        Files.createFile(rootDir.resolve("a_first.jpg"));
        Files.createFile(rootDir.resolve("m_middle.jpg"));

        // Act
        Optional<PictureInfo> result = repository.getPictureInfo("/m_middle.jpg");

        // Assert
        assertThat(result).isPresent();
        assertThat(result.get().siblingNames()).containsExactly("a_first.jpg", "m_middle.jpg", "z_last.jpg");
    }

    @Test
    void getPictureInfo_singlePictureReturnsSingleSibling() throws IOException {
        // Arrange
        Files.createFile(rootDir.resolve("only.jpg"));

        // Act
        Optional<PictureInfo> result = repository.getPictureInfo("/only.jpg");

        // Assert
        assertThat(result).isPresent();
        assertThat(result.get().siblingNames()).containsExactly("only.jpg");
    }

    @Test
    void getPictureInfo_nonImageFilesAreExcludedFromSiblings() throws IOException {
        // Arrange
        Files.createFile(rootDir.resolve("photo.jpg"));
        Files.createFile(rootDir.resolve("document.pdf"));

        // Act
        Optional<PictureInfo> result = repository.getPictureInfo("/photo.jpg");

        // Assert
        assertThat(result).isPresent();
        assertThat(result.get().siblingNames()).containsExactly("photo.jpg");
    }

    @Test
    void getPictureInfo_returnsEmptyForMissingFile() throws IOException {
        // Act & Assert
        Optional<PictureInfo> result = repository.getPictureInfo("/missing.jpg");
        assertThat(result).isEmpty();
    }

    @Test
    void getPictureInfo_returnsEmptyForNonImageFile() throws IOException {
        // Arrange
        Files.createFile(rootDir.resolve("notes.txt"));

        // Act
        Optional<PictureInfo> result = repository.getPictureInfo("/notes.txt");

        // Assert
        assertThat(result).isEmpty();
    }

    @Test
    void getPictureInfo_throwsSecurityExceptionForPathTraversal() {
        // Act & Assert
        assertThatThrownBy(() -> repository.getPictureInfo("/../etc/passwd"))
                .isInstanceOf(SecurityException.class);
    }

    // -------------------------------------------------------------------------
    // moveToTrash
    // -------------------------------------------------------------------------

    @Test
    void moveToTrash_returnsEmptyForMissingFile() {
        // Act & Assert
        Optional<Boolean> result = repository.moveToTrash("/missing.jpg");
        assertThat(result).isEmpty();
    }

    @Test
    void moveToTrash_returnsEmptyForNonImageFile() throws IOException {
        // Arrange
        Files.createFile(rootDir.resolve("notes.txt"));

        // Act
        Optional<Boolean> result = repository.moveToTrash("/notes.txt");

        // Assert
        assertThat(result).isEmpty();
    }

    @Test
    void moveToTrash_returnsFalseWhenTrashMoverReturnsFalse() throws IOException {
        // Arrange
        Files.createFile(rootDir.resolve("photo.jpg"));
        Settings settings = new Settings(rootDir, 8080, "admin", "secret", PANIC_SETTINGS);
        FilesystemPictureRepository repo = new FilesystemPictureRepository(settings, path -> false);

        // Act
        Optional<Boolean> result = repo.moveToTrash("/photo.jpg");

        // Assert
        assertThat(result).contains(false);
    }

    @Test
    void moveToTrash_returnsTrueAndPassesCorrectPathToTrashMover() throws IOException {
        // Arrange
        Path imagePath = rootDir.resolve("photo.jpg");
        Files.createFile(imagePath);
        AtomicReference<Path> capturedPath = new AtomicReference<>();
        Settings settings = new Settings(rootDir, 8080, "admin", "secret", PANIC_SETTINGS);
        FilesystemPictureRepository repo = new FilesystemPictureRepository(settings, path -> {
            capturedPath.set(path);
            return true;
        });

        // Act
        Optional<Boolean> result = repo.moveToTrash("/photo.jpg");

        // Assert
        assertThat(result).contains(true);
        assertThat(capturedPath.get()).isEqualTo(imagePath);
    }

    @Test
    void moveToTrash_throwsSecurityExceptionForPathTraversal() {
        // Act & Assert
        assertThatThrownBy(() -> repository.moveToTrash("/../etc/passwd"))
                .isInstanceOf(SecurityException.class);
    }

    // -------------------------------------------------------------------------
    // getImageInfo
    // -------------------------------------------------------------------------

    @Test
    void getImageInfo_returnsMetadataForValidImage() throws IOException {
        // Arrange
        byte[] content = "image data".getBytes(StandardCharsets.UTF_8);
        Files.write(rootDir.resolve("photo.jpg"), content);

        // Act
        Optional<ImageInfo> result = repository.getImageInfo("/photo.jpg");

        // Assert
        assertThat(result).isPresent();
        assertThat(result.get().size()).isEqualTo(content.length);
        assertThat(result.get().lastModifiedMillis()).isPositive();
        assertThat(result.get().filename()).isEqualTo("photo.jpg");
    }

    @Test
    void getImageInfo_returnsEmptyForMissingFile() throws IOException {
        // Act & Assert
        Optional<ImageInfo> result = repository.getImageInfo("/missing.jpg");
        assertThat(result).isEmpty();
    }

    @Test
    void getImageInfo_returnsEmptyForNonImageFile() throws IOException {
        // Arrange
        Files.createFile(rootDir.resolve("notes.txt"));

        // Act
        Optional<ImageInfo> result = repository.getImageInfo("/notes.txt");

        // Assert
        assertThat(result).isEmpty();
    }

    @Test
    void getImageInfo_throwsSecurityExceptionForPathTraversal() {
        // Act & Assert
        assertThatThrownBy(() -> repository.getImageInfo("/../etc/passwd"))
                .isInstanceOf(SecurityException.class);
    }

    // -------------------------------------------------------------------------
    // openImage
    // -------------------------------------------------------------------------

    @Test
    void openImage_returnsStreamWithCorrectContent() throws IOException {
        // Arrange
        byte[] content = new byte[]{1, 2, 3, 4, 5};
        Files.write(rootDir.resolve("photo.jpg"), content);

        // Act
        Optional<InputStream> result = repository.openImage("/photo.jpg");

        // Assert
        assertThat(result).isPresent();
        try (InputStream in = result.get()) {
            assertThat(in.readAllBytes()).containsExactly(content);
        }
    }

    @Test
    void openImage_returnsEmptyForMissingFile() throws IOException {
        // Act & Assert
        Optional<InputStream> result = repository.openImage("/missing.jpg");
        assertThat(result).isEmpty();
    }

    @Test
    void openImage_returnsEmptyForNonImageFile() throws IOException {
        // Arrange
        Files.createFile(rootDir.resolve("notes.txt"));

        // Act
        Optional<InputStream> result = repository.openImage("/notes.txt");

        // Assert
        assertThat(result).isEmpty();
    }

    @Test
    void openImage_throwsSecurityExceptionForPathTraversal() {
        // Act & Assert
        assertThatThrownBy(() -> repository.openImage("/../etc/passwd"))
                .isInstanceOf(SecurityException.class);
    }

    // -------------------------------------------------------------------------
    // getFavoritesAlbumInfo
    // -------------------------------------------------------------------------

    @Test
    void getFavoritesAlbumInfo_returnsEmptyListWhenNoFavoritesFile() throws IOException {
        // Act
        Optional<AlbumInfo> result = repository.getFavoritesAlbumInfo();

        // Assert
        assertThat(result).isPresent();
        assertThat(result.get().pictures()).isEmpty();
        assertThat(result.get().albums()).isEmpty();
    }

    @Test
    void getFavoritesAlbumInfo_returnsOnlyExistingImages() throws IOException {
        // Arrange
        Files.createFile(rootDir.resolve("photo.jpg"));
        Files.write(rootDir.resolve(".favorites"), List.of("photo.jpg", "missing.jpg"), StandardCharsets.UTF_8);

        // Act
        Optional<AlbumInfo> result = repository.getFavoritesAlbumInfo();

        // Assert
        assertThat(result).isPresent();
        assertThat(result.get().pictures()).containsExactly("photo.jpg");
    }

    // -------------------------------------------------------------------------
    // addFavorite
    // -------------------------------------------------------------------------

    @Test
    void addFavorite_returnsTrueWhenNewlyAdded() throws IOException {
        // Arrange
        Files.createFile(rootDir.resolve("photo.jpg"));

        // Act
        Optional<Boolean> result = repository.addFavorite("/photo.jpg");

        // Assert
        assertThat(result).contains(true);
        assertThat(Files.readAllLines(rootDir.resolve(".favorites"), StandardCharsets.UTF_8))
                .containsExactly("photo.jpg");
    }

    @Test
    void addFavorite_returnsFalseWhenAlreadyPresent() throws IOException {
        // Arrange
        Files.createFile(rootDir.resolve("photo.jpg"));
        Files.write(rootDir.resolve(".favorites"), List.of("photo.jpg"), StandardCharsets.UTF_8);

        // Act
        Optional<Boolean> result = repository.addFavorite("/photo.jpg");

        // Assert
        assertThat(result).contains(false);
    }

    @Test
    void addFavorite_returnsEmptyForNonImageFile() throws IOException {
        // Arrange
        Files.createFile(rootDir.resolve("notes.txt"));

        // Act
        Optional<Boolean> result = repository.addFavorite("/notes.txt");

        // Assert
        assertThat(result).isEmpty();
    }

    @Test
    void addFavorite_throwsSecurityExceptionForPathTraversal() {
        // Act & Assert
        assertThatThrownBy(() -> repository.addFavorite("/../etc/passwd"))
                .isInstanceOf(SecurityException.class);
    }

    // -------------------------------------------------------------------------
    // removeFavorite
    // -------------------------------------------------------------------------

    @Test
    void removeFavorite_returnsTrueWhenRemoved() throws IOException {
        // Arrange
        Files.createFile(rootDir.resolve("photo.jpg"));
        Files.write(rootDir.resolve(".favorites"), List.of("photo.jpg"), StandardCharsets.UTF_8);

        // Act
        Optional<Boolean> result = repository.removeFavorite("/photo.jpg");

        // Assert
        assertThat(result).contains(true);
        assertThat(Files.readAllLines(rootDir.resolve(".favorites"), StandardCharsets.UTF_8)).isEmpty();
    }

    @Test
    void removeFavorite_returnsFalseWhenNotPresent() throws IOException {
        // Arrange
        Files.createFile(rootDir.resolve("photo.jpg"));

        // Act
        Optional<Boolean> result = repository.removeFavorite("/photo.jpg");

        // Assert
        assertThat(result).contains(false);
    }

    @Test
    void removeFavorite_returnsEmptyForNonImageFile() throws IOException {
        // Arrange
        Files.createFile(rootDir.resolve("notes.txt"));

        // Act
        Optional<Boolean> result = repository.removeFavorite("/notes.txt");

        // Assert
        assertThat(result).isEmpty();
    }

    // -------------------------------------------------------------------------
    // isFavorite
    // -------------------------------------------------------------------------

    @Test
    void isFavorite_returnsTrueWhenInFavorites() throws IOException {
        // Arrange
        Files.createFile(rootDir.resolve("photo.jpg"));
        Files.write(rootDir.resolve(".favorites"), List.of("photo.jpg"), StandardCharsets.UTF_8);

        // Act
        Optional<Boolean> result = repository.isFavorite("/photo.jpg");

        // Assert
        assertThat(result).contains(true);
    }

    @Test
    void isFavorite_returnsFalseWhenNotInFavorites() throws IOException {
        // Arrange
        Files.createFile(rootDir.resolve("photo.jpg"));

        // Act
        Optional<Boolean> result = repository.isFavorite("/photo.jpg");

        // Assert
        assertThat(result).contains(false);
    }

    @Test
    void isFavorite_returnsEmptyForNonImageFile() throws IOException {
        // Arrange
        Files.createFile(rootDir.resolve("notes.txt"));

        // Act
        Optional<Boolean> result = repository.isFavorite("/notes.txt");

        // Assert
        assertThat(result).isEmpty();
    }

    // -------------------------------------------------------------------------
    // moveToTrash (favorites integration)
    // -------------------------------------------------------------------------

    @Test
    void moveToTrash_removesFromFavoritesOnSuccess() throws IOException {
        // Arrange
        Files.createFile(rootDir.resolve("photo.jpg"));
        Files.write(rootDir.resolve(".favorites"), List.of("photo.jpg"), StandardCharsets.UTF_8);
        Settings settings = new Settings(rootDir, 8080, "admin", "secret", PANIC_SETTINGS);
        FilesystemPictureRepository repo = new FilesystemPictureRepository(settings, path -> true);

        // Act
        repo.moveToTrash("/photo.jpg");

        // Assert
        assertThat(Files.readAllLines(rootDir.resolve(".favorites"), StandardCharsets.UTF_8)).isEmpty();
    }

    @Test
    void moveToTrash_doesNotModifyFavoritesWhenTrashFails() throws IOException {
        // Arrange
        Files.createFile(rootDir.resolve("photo.jpg"));
        Files.write(rootDir.resolve(".favorites"), List.of("photo.jpg"), StandardCharsets.UTF_8);
        Settings settings = new Settings(rootDir, 8080, "admin", "secret", PANIC_SETTINGS);
        FilesystemPictureRepository repo = new FilesystemPictureRepository(settings, path -> false);

        // Act
        repo.moveToTrash("/photo.jpg");

        // Assert
        assertThat(Files.readAllLines(rootDir.resolve(".favorites"), StandardCharsets.UTF_8))
                .containsExactly("photo.jpg");
    }
}
