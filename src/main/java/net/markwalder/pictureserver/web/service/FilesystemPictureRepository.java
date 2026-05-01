package net.markwalder.pictureserver.web.service;

import java.awt.Desktop;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import net.markwalder.pictureserver.config.Settings;
import net.markwalder.pictureserver.web.ImageTypes;
import net.markwalder.pictureserver.web.PathSafety;

public final class FilesystemPictureRepository implements PictureRepository {

    static final Set<String> IGNORED_FOLDER_NAMES = Set.of(
            "Photos Library.photoslibrary",
            "Photo Booth Library",
            "Favorites"
    );

    @FunctionalInterface
    public interface TrashMover {
        boolean moveToTrash(Path imagePath);
    }

    private final Settings settings;
    private final TrashMover trashMover;

    public FilesystemPictureRepository(Settings settings) {
        this(settings, FilesystemPictureRepository::moveToTrashWithDesktop);
    }

    public FilesystemPictureRepository(Settings settings, TrashMover trashMover) {
        this.settings = settings;
        this.trashMover = trashMover;
    }

    @Override
    public Optional<AlbumInfo> getAlbumInfo(String webPath) throws IOException {
        // Resolve and validate path
        Path albumFsPath = PathSafety.resolveSafePath(webPath, settings.rootDirectory());
        if (!Files.isDirectory(albumFsPath)) {
            return Optional.empty();
        }

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

        return Optional.of(new AlbumInfo(albums, albumFirstImages, pictures));
    }

    @Override
    public Optional<PictureInfo> getPictureInfo(String webPath) throws IOException {
        // Resolve and validate path
        Path pictureFsPath = PathSafety.resolveSafePath(webPath, settings.rootDirectory());
        if (!Files.isRegularFile(pictureFsPath) || !ImageTypes.isImageFile(pictureFsPath.getFileName().toString())) {
            return Optional.empty();
        }

        // List image siblings in the same directory
        List<String> siblingNames = new ArrayList<>();
        Path parentDir = pictureFsPath.getParent();
        if (parentDir != null) {
            try (Stream<Path> list = Files.list(parentDir)) {
                list.filter(p -> Files.isRegularFile(p) && ImageTypes.isImageFile(p.getFileName().toString()))
                        .sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT)))
                        .forEachOrdered(p -> siblingNames.add(p.getFileName().toString()));
            }
        }
        return Optional.of(new PictureInfo(siblingNames));
    }

    @Override
    public Optional<Boolean> moveToTrash(String webPath) {
        // Resolve and validate path
        Path pictureFsPath = PathSafety.resolveSafePath(webPath, settings.rootDirectory());
        if (!Files.isRegularFile(pictureFsPath) || !ImageTypes.isImageFile(pictureFsPath.getFileName().toString())) {
            return Optional.empty();
        }

        // Move to trash
        boolean moved = trashMover.moveToTrash(pictureFsPath);

        // Remove from favorites if moved successfully
        if (moved) {
            String relativePath = toRelativePath(pictureFsPath);
            synchronized (this) {
                List<String> lines = readFavoritesRaw();
                if (lines.remove(relativePath)) {
                    writeFavoritesRaw(lines);
                }
            }
        }

        return Optional.of(moved);
    }

    @Override
    public synchronized Optional<AlbumInfo> getFavoritesAlbumInfo() throws IOException {
        // Read and filter favorites to only existing image files
        List<String> raw = readFavoritesRaw();
        List<String> valid = new ArrayList<>();
        for (String entry : raw) {
            Path fsPath = settings.rootDirectory().resolve(entry);
            if (Files.isRegularFile(fsPath) && ImageTypes.isImageFile(fsPath.getFileName().toString())) {
                valid.add(entry);
            }
        }
        return Optional.of(new AlbumInfo(List.of(), Map.of(), valid));
    }

    @Override
    public synchronized Optional<Boolean> addFavorite(String webPath) throws IOException {
        // Resolve and validate path
        Path fsPath = PathSafety.resolveSafePath(webPath, settings.rootDirectory());
        if (!Files.isRegularFile(fsPath) || !ImageTypes.isImageFile(fsPath.getFileName().toString())) {
            return Optional.empty();
        }

        // Add to favorites if not already present
        String relativePath = toRelativePath(fsPath);
        List<String> lines = readFavoritesRaw();
        if (lines.contains(relativePath)) {
            return Optional.of(false);
        }
        lines.add(relativePath);
        writeFavoritesRaw(lines);
        return Optional.of(true);
    }

    @Override
    public synchronized Optional<Boolean> removeFavorite(String webPath) throws IOException {
        // Resolve and validate path
        Path fsPath = PathSafety.resolveSafePath(webPath, settings.rootDirectory());
        if (!Files.isRegularFile(fsPath) || !ImageTypes.isImageFile(fsPath.getFileName().toString())) {
            return Optional.empty();
        }

        // Remove from favorites
        String relativePath = toRelativePath(fsPath);
        List<String> lines = readFavoritesRaw();
        if (!lines.remove(relativePath)) {
            return Optional.of(false);
        }
        writeFavoritesRaw(lines);
        return Optional.of(true);
    }

    @Override
    public synchronized Optional<Boolean> isFavorite(String webPath) throws IOException {
        // Resolve and validate path
        Path fsPath = PathSafety.resolveSafePath(webPath, settings.rootDirectory());
        if (!Files.isRegularFile(fsPath) || !ImageTypes.isImageFile(fsPath.getFileName().toString())) {
            return Optional.empty();
        }

        // Check favorites list
        String relativePath = toRelativePath(fsPath);
        return Optional.of(readFavoritesRaw().contains(relativePath));
    }

    @Override
    public Optional<ImageInfo> getImageInfo(String webPath) throws IOException {
        // Resolve and validate path
        Path imageFsPath = PathSafety.resolveSafePath(webPath, settings.rootDirectory());
        if (!Files.isRegularFile(imageFsPath) || !ImageTypes.isImageFile(imageFsPath.getFileName().toString())) {
            return Optional.empty();
        }

        // Read file metadata
        long size = Files.size(imageFsPath);
        long lastModifiedMillis = Files.getLastModifiedTime(imageFsPath).toMillis();
        String filename = imageFsPath.getFileName().toString();
        return Optional.of(new ImageInfo(size, lastModifiedMillis, filename));
    }

    @Override
    public Optional<InputStream> openImage(String webPath) throws IOException {
        // Resolve and validate path
        Path imageFsPath = PathSafety.resolveSafePath(webPath, settings.rootDirectory());
        if (!Files.isRegularFile(imageFsPath) || !ImageTypes.isImageFile(imageFsPath.getFileName().toString())) {
            return Optional.empty();
        }

        // Open image stream
        return Optional.of(Files.newInputStream(imageFsPath));
    }

    private String toRelativePath(Path fsPath) {
        return settings.rootDirectory().relativize(fsPath).toString();
    }

    private List<String> readFavoritesRaw() {
        Path favoritesFile = settings.rootDirectory().resolve(".favorites");
        try {
            return new ArrayList<>(Files.readAllLines(favoritesFile, StandardCharsets.UTF_8));
        } catch (NoSuchFileException ignored) {
            return new ArrayList<>();
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    private void writeFavoritesRaw(List<String> lines) {
        Path favoritesFile = settings.rootDirectory().resolve(".favorites");
        try {
            Files.write(favoritesFile, lines, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    private static boolean moveToTrashWithDesktop(Path imagePath) {
        if (!Desktop.isDesktopSupported()) {
            return false;
        }
        Desktop desktop = Desktop.getDesktop();
        if (!desktop.isSupported(Desktop.Action.MOVE_TO_TRASH)) {
            return false;
        }
        return desktop.moveToTrash(imagePath.toFile());
    }
}
