package net.markwalder.pictureserver.web.service;

import java.awt.Desktop;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
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
            "Photo Booth Library"
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
        return Optional.of(trashMover.moveToTrash(pictureFsPath));
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
