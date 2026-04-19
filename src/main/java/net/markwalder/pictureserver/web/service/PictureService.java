package net.markwalder.pictureserver.web.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import net.markwalder.pictureserver.web.ImageTypes;

public final class PictureService {

    private PictureService() {
    }

    public record PictureInfo(List<String> siblingNames) {
    }

    public static PictureInfo getPictureInfo(Path pictureFsPath) throws IOException {
        List<String> siblingNames = new ArrayList<>();
        Path parentDir = pictureFsPath.getParent();
        if (parentDir != null) {
            try (Stream<Path> list = Files.list(parentDir)) {
                list.filter(p -> Files.isRegularFile(p) && ImageTypes.isImageFile(p.getFileName().toString()))
                        .sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT)))
                        .forEachOrdered(p -> siblingNames.add(p.getFileName().toString()));
            }
        }
        return new PictureInfo(siblingNames);
    }
}
