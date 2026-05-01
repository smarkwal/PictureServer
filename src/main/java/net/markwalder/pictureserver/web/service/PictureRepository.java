package net.markwalder.pictureserver.web.service;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface PictureRepository {

    record AlbumInfo(List<String> albums, Map<String, String> albumFirstImages, List<String> pictures) {
    }

    record PictureInfo(List<String> siblingNames) {
    }

    record ImageInfo(long size, long lastModifiedMillis, String filename) {
    }

    // Returns empty if the path is not a valid album directory.
    // Note: "/Favorites" is not valid here — use getFavoritesAlbumInfo() instead.
    // Throws SecurityException on path traversal attempt.
    Optional<AlbumInfo> getAlbumInfo(String webPath) throws IOException;

    // Returns the virtual Favorites album: pictures = valid-existing favorites (relative paths
    // without leading slash, e.g. "vacation/beach.jpg"), albums = [], albumFirstImages = {}.
    // Missing files are filtered out silently.
    Optional<AlbumInfo> getFavoritesAlbumInfo() throws IOException;

    // Returns empty if the path is not a valid image file.
    // Throws SecurityException on path traversal attempt.
    Optional<PictureInfo> getPictureInfo(String webPath) throws IOException;

    // Returns empty if the file does not exist.
    // Returns Optional.of(false) if trash is not supported on this system.
    // Returns Optional.of(true) on success.
    // Throws SecurityException on path traversal attempt.
    Optional<Boolean> moveToTrash(String webPath);

    // Returns empty if webPath is not a valid image file.
    // Returns of(true) if newly added, of(false) if already a favorite.
    // Throws SecurityException on path traversal attempt.
    Optional<Boolean> addFavorite(String webPath) throws IOException;

    // Returns empty if webPath is not a valid image file.
    // Returns of(true) if the entry was removed, of(false) if it was not present.
    // Throws SecurityException on path traversal attempt.
    Optional<Boolean> removeFavorite(String webPath) throws IOException;

    // Returns empty if webPath is not a valid image file.
    // Returns of(true) if in favorites, of(false) if not.
    // Throws SecurityException on path traversal attempt.
    Optional<Boolean> isFavorite(String webPath) throws IOException;

    // Returns empty if the path is not a valid image file.
    // Throws SecurityException on path traversal attempt.
    Optional<ImageInfo> getImageInfo(String webPath) throws IOException;

    // Returns empty if the path is not a valid image file.
    // Throws SecurityException on path traversal attempt.
    Optional<InputStream> openImage(String webPath) throws IOException;
}
