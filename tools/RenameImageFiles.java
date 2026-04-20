import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.*;
import java.util.*;
import java.util.stream.*;

public class RenameImageFiles {

    private static final Set<String> IMAGE_EXTENSIONS = Set.of(
            ".jpg", ".jpeg", ".png", ".gif", ".bmp", ".webp", ".tiff", ".tif"
    );

    public static void main(String[] args) throws IOException {
        Path root = Path.of("").toAbsolutePath();
        Scanner scanner = new Scanner(System.in);

        System.out.println("Root: " + root);

        List<Path> dirs = collectDirectories(root);
        if (dirs.isEmpty()) {
            System.out.println("No subdirectories found.");
            return;
        }

        for (Path dir : dirs) {
            processFolder(root, dir, scanner);
        }

        System.out.println();
        System.out.println("Done.");
    }

    private static List<Path> collectDirectories(Path root) throws IOException {
        List<Path> dirs = new ArrayList<>();
        Files.walkFileTree(root, Set.of(), Integer.MAX_VALUE, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (dir.equals(root)) return FileVisitResult.CONTINUE;
                if (attrs.isSymbolicLink()) return FileVisitResult.SKIP_SUBTREE;
                try {
                    if (Files.isHidden(dir)) return FileVisitResult.SKIP_SUBTREE;
                } catch (IOException e) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                dirs.add(dir);
                return FileVisitResult.CONTINUE;
            }
        });
        dirs.sort(Comparator.naturalOrder());
        return dirs;
    }

    private static void processFolder(Path root, Path dir, Scanner scanner) throws IOException {
        List<Path> imageFiles;
        try (Stream<Path> stream = Files.list(dir)) {
            imageFiles = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> !isHidden(p))
                    .filter(RenameImageFiles::isImageFile)
                    .sorted(Comparator.comparing(p -> p.getFileName().toString(), String.CASE_INSENSITIVE_ORDER))
                    .collect(Collectors.toList());
        }

        if (imageFiles.isEmpty()) return;

        String folderName = normalizeFolderName(dir.getFileName().toString());

        record RenameEntry(Path source, String oldName, String newName) {
            boolean needsRename() { return !oldName.equals(newName); }
        }

        List<RenameEntry> entries = new ArrayList<>();
        int seq = 1;
        for (Path file : imageFiles) {
            String oldName = file.getFileName().toString();
            String ext = extension(oldName).toLowerCase();
            String newName = "%s-%04d%s".formatted(folderName, seq++, ext);
            entries.add(new RenameEntry(file, oldName, newName));
        }

        System.out.println();
        System.out.println("Folder: " + root.relativize(dir));
        System.out.println();

        int maxOld = entries.stream().mapToInt(e -> e.oldName().length()).max().orElse(0);
        int maxNew = entries.stream().mapToInt(e -> e.newName().length()).max().orElse(0);
        String fmt = "  %-" + maxOld + "s  ->  %-" + maxNew + "s%s%n";

        for (var e : entries) {
            String label = e.needsRename() ? "" : "  (not renamed)";
            System.out.printf(fmt, e.oldName(), e.newName(), label);
        }

        System.out.println();

        boolean anyToRename = entries.stream().anyMatch(RenameEntry::needsRename);
        if (!anyToRename) {
            System.out.println("  All files already have correct names. Skipping.");
            return;
        }

        // Check for conflicts with files not in the rename batch
        Set<String> batchOldNames = entries.stream().map(RenameEntry::oldName).collect(Collectors.toSet());
        List<String> conflicts = entries.stream()
                .filter(RenameEntry::needsRename)
                .map(RenameEntry::newName)
                .filter(n -> Files.exists(dir.resolve(n)) && !batchOldNames.contains(n))
                .collect(Collectors.toList());

        if (!conflicts.isEmpty()) {
            System.out.println("  WARNING: Target names conflict with existing files not in this batch:");
            conflicts.forEach(c -> System.out.println("    " + c));
            System.out.println("  Skipping folder.");
            return;
        }

        System.out.print("  Rename files? [y/n]: ");
        String answer = scanner.nextLine().trim().toLowerCase();

        if (!answer.equals("y")) {
            System.out.println("  Skipped.");
            return;
        }

        // Phase 1: move each file to a unique temp name to avoid mid-batch collisions
        List<Map.Entry<Path, Path>> tempMoves = new ArrayList<>();
        for (var e : entries) {
            if (!e.needsRename()) continue;
            Path src = e.source();
            Path tmp = dir.resolve(UUID.randomUUID() + ".tmp");
            FileTime mtime = Files.getLastModifiedTime(src);
            Files.move(src, tmp);
            Files.setLastModifiedTime(tmp, mtime);
            tempMoves.add(Map.entry(tmp, dir.resolve(e.newName())));
        }

        // Phase 2: move each temp file to its final name
        for (var move : tempMoves) {
            Path tmp = move.getKey();
            Path dest = move.getValue();
            FileTime mtime = Files.getLastModifiedTime(tmp);
            Files.move(tmp, dest);
            Files.setLastModifiedTime(dest, mtime);
        }

        System.out.println("  Renamed " + tempMoves.size() + " file(s).");
    }

    private static String normalizeFolderName(String name) {
        String result = name.toLowerCase()
                .replace(' ', '-')
                .replaceAll("[^a-z0-9-]", "")
                .replaceAll("-{2,}", "-")  // collapse consecutive dashes
                .replaceAll("^-+|-+$", ""); // trim leading/trailing dashes
        return result.isEmpty() ? "image" : result;
    }

    private static boolean isImageFile(Path file) {
        return IMAGE_EXTENSIONS.contains(extension(file.getFileName().toString()).toLowerCase());
    }

    private static String extension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot) : "";
    }

    private static boolean isHidden(Path path) {
        try {
            return Files.isHidden(path);
        } catch (IOException e) {
            return true;
        }
    }
}
