package net.markwalder.pictureserver.web.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import net.markwalder.pictureserver.web.ImageTypes;

public final class AlbumService {

    static final Set<String> IGNORED_FOLDER_NAMES = Set.of(
            "Photos Library.photoslibrary",
            "Photo Booth Library"
    );

    private AlbumService() {
    }

    public record AlbumInfo(List<String> albums, Map<String, String> albumFirstImages, List<String> pictures) {
    }

    public static AlbumInfo listAlbum(Path albumFsPath) throws IOException {
        List<String> albums = new ArrayList<>();
        List<String> pictures = new ArrayList<>();

        // Scan directory and categorize entries
        try (Stream<Path> list = Files.list(albumFsPath)) {
            list.sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .forEachOrdered(child -> {
                        String name = child.getFileName().toString();
                        if (Files.isDirectory(child) && !IGNORED_FOLDER_NAMES.contains(name)) {
                            albums.add(name);
                        } else if (Files.isRegularFile(child) && ImageTypes.isImageFile(name)) {
                            pictures.add(name);
                        }
                    });
        }

        // Find preview image for each album
        Map<String, String> albumFirstImages = new LinkedHashMap<>();
        for (String album : albums) {
            Path albumDir = albumFsPath.resolve(album);
            try (Stream<Path> albumList = Files.list(albumDir)) {
                albumList
                        .filter(p -> Files.isRegularFile(p) && ImageTypes.isImageFile(p.getFileName().toString()))
                        .min(Comparator.comparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT)))
                        .ifPresent(p -> albumFirstImages.put(album, p.getFileName().toString()));
            } catch (IOException ignored) {
                // album directory not accessible; no preview
            }
        }

        return new AlbumInfo(albums, albumFirstImages, pictures);
    }
}
