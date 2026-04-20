import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class FormatMarkdownTables {

    private static final List<String> SKIP_DIRECTORY_NAMES =
            List.of(".git", ".gradle", "build", "out", "node_modules");

    private FormatMarkdownTables() {}

    public static void main(String[] args) throws IOException {
        Arguments arguments = Arguments.parse(args);
        List<Path> markdownFiles = collectMarkdownFiles(arguments.rootDirectory());

        int changedFiles = 0;
        int changedTables = 0;

        for (Path file : markdownFiles) {
            String originalText = Files.readString(file, StandardCharsets.UTF_8);
            FormatResult result = formatMarkdownTables(originalText);
            if (!result.changed()) {
                continue;
            }

            changedFiles++;
            changedTables += result.changedTables();

            if (arguments.writeChanges()) {
                Files.writeString(file, result.text(), StandardCharsets.UTF_8);
                System.out.println("Formatted " + file);
            } else {
                System.out.println("Would format " + file);
            }
        }

        if (changedFiles == 0) {
            System.out.println("No markdown tables needed formatting.");
            return;
        }

        String summary = "Tables needing formatting: " + changedTables + " across " + changedFiles + " file(s).";
        System.out.println(summary);

        if (!arguments.writeChanges()) {
            System.exit(1);
        }
    }

    private static List<Path> collectMarkdownFiles(Path rootDirectory) throws IOException {
        try (var stream = Files.walk(rootDirectory, Integer.MAX_VALUE, FileVisitOption.FOLLOW_LINKS)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".md"))
                    .filter(path -> !shouldSkip(path, rootDirectory))
                    .sorted()
                    .toList();
        }
    }

    private static boolean shouldSkip(Path path, Path rootDirectory) {
        Path normalizedRoot = rootDirectory.toAbsolutePath().normalize();
        Path normalizedPath = path.toAbsolutePath().normalize();

        if (!normalizedPath.startsWith(normalizedRoot)) {
            return true;
        }

        Path relativePath = normalizedRoot.relativize(normalizedPath);
        for (Path segment : relativePath) {
            if (SKIP_DIRECTORY_NAMES.contains(segment.toString())) {
                return true;
            }
        }

        return false;
    }

    private static FormatResult formatMarkdownTables(String text) {
        List<String> lines = splitLines(text);
        List<String> output = new ArrayList<>();

        boolean inFence = false;
        int changedTables = 0;

        for (int i = 0; i < lines.size();) {
            String line = lines.get(i);
            if (startsFence(line)) {
                inFence = !inFence;
                output.add(line);
                i++;
                continue;
            }

            if (inFence || !isCandidateRow(line) || i + 1 >= lines.size()) {
                output.add(line);
                i++;
                continue;
            }

            String separatorLine = lines.get(i + 1);
            if (!isSeparatorRow(separatorLine)) {
                output.add(line);
                i++;
                continue;
            }

            String indentation = leadingWhitespace(line);
            List<String> tableLines = new ArrayList<>();
            tableLines.add(line);
            tableLines.add(separatorLine);

            int j = i + 2;
            while (j < lines.size()) {
                String candidate = lines.get(j);
                if (candidate.isBlank()) {
                    break;
                }
                if (!isCandidateRow(candidate)) {
                    break;
                }
                if (!Objects.equals(indentation, leadingWhitespace(candidate))) {
                    break;
                }
                tableLines.add(candidate);
                j++;
            }

            List<String> formattedTable = formatTable(tableLines, indentation);
            if (!tableLines.equals(formattedTable)) {
                changedTables++;
            }
            output.addAll(formattedTable);
            i = j;
        }

        String formattedText = String.join("\n", output);

        return new FormatResult(formattedText, changedTables > 0, changedTables);
    }

    private static List<String> splitLines(String text) {
        return new ArrayList<>(List.of(text.split("\\R", -1)));
    }

    private static boolean startsFence(String line) {
        String trimmed = line.stripLeading();
        return trimmed.startsWith("```") || trimmed.startsWith("~~~");
    }

    private static boolean isCandidateRow(String line) {
        String content = line.strip();
        return !content.isEmpty() && hasUnescapedPipe(content);
    }

    private static boolean hasUnescapedPipe(String value) {
        boolean escaped = false;
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (current == '\\' && !escaped) {
                escaped = true;
                continue;
            }
            if (current == '|' && !escaped) {
                return true;
            }
            escaped = false;
        }
        return false;
    }

    private static boolean isSeparatorRow(String line) {
        List<String> cells = splitCells(line.strip());
        if (cells.isEmpty()) {
            return false;
        }

        for (String cell : cells) {
            String trimmed = cell.trim();
            if (trimmed.isEmpty()) {
                return false;
            }
            if (!trimmed.matches(":?-{3,}:?")) {
                return false;
            }
        }

        return true;
    }

    private static List<String> formatTable(List<String> tableLines, String indentation) {
        List<List<String>> rows = new ArrayList<>();
        for (String row : tableLines) {
            String content = row.substring(indentation.length()).strip();
            rows.add(splitCells(content));
        }

        int columns = rows.stream().mapToInt(List::size).max().orElse(0);
        List<Integer> widths = new ArrayList<>();
        for (int column = 0; column < columns; column++) {
            int maxWidth = 3;
            for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                if (rowIndex == 1) {
                    continue;
                }
                String cell = getCell(rows.get(rowIndex), column);
                maxWidth = Math.max(maxWidth, cell.length());
            }
            widths.add(maxWidth);
        }

        List<Alignment> alignments = new ArrayList<>();
        for (int column = 0; column < columns; column++) {
            alignments.add(parseAlignment(getCell(rows.get(1), column)));
        }

        List<String> formattedRows = new ArrayList<>();

        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            if (rowIndex == 1) {
                formattedRows.add(formatSeparatorRow(widths, alignments, indentation));
                continue;
            }
            formattedRows.add(formatContentRow(rows.get(rowIndex), widths, indentation));
        }

        return formattedRows;
    }

    private static String formatContentRow(List<String> row, List<Integer> widths, String indentation) {
        StringBuilder builder = new StringBuilder(indentation).append("|");

        for (int column = 0; column < widths.size(); column++) {
            String cell = getCell(row, column);
            builder.append(' ').append(padRight(cell, widths.get(column))).append(' ').append('|');
        }

        return builder.toString();
    }

    private static String formatSeparatorRow(List<Integer> widths, List<Alignment> alignments, String indentation) {
        StringBuilder builder = new StringBuilder(indentation).append("|");

        for (int column = 0; column < widths.size(); column++) {
            int width = Math.max(3, widths.get(column));
            String token = separatorToken(width, alignments.get(column));
            builder.append(' ').append(padRight(token, width)).append(' ').append('|');
        }

        return builder.toString();
    }

    private static String separatorToken(int width, Alignment alignment) {
        return switch (alignment) {
            case LEFT -> ":" + "-".repeat(Math.max(2, width - 1));
            case RIGHT -> "-".repeat(Math.max(2, width - 1)) + ":";
            case CENTER -> ":" + "-".repeat(Math.max(1, width - 2)) + ":";
            case NONE -> "-".repeat(Math.max(3, width));
        };
    }

    private static Alignment parseAlignment(String separatorCell) {
        String trimmed = separatorCell.trim();
        boolean left = trimmed.startsWith(":");
        boolean right = trimmed.endsWith(":");

        if (left && right) {
            return Alignment.CENTER;
        }
        if (left) {
            return Alignment.LEFT;
        }
        if (right) {
            return Alignment.RIGHT;
        }
        return Alignment.NONE;
    }

    private static String getCell(List<String> row, int column) {
        if (column < row.size()) {
            return row.get(column);
        }
        return "";
    }

    private static List<String> splitCells(String row) {
        String trimmed = row.strip();
        if (trimmed.startsWith("|")) {
            trimmed = trimmed.substring(1);
        }
        if (trimmed.endsWith("|")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }

        List<String> cells = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean escaped = false;

        for (int i = 0; i < trimmed.length(); i++) {
            char value = trimmed.charAt(i);
            if (value == '\\' && !escaped) {
                escaped = true;
                current.append(value);
                continue;
            }
            if (value == '|' && !escaped) {
                cells.add(current.toString().trim());
                current.setLength(0);
                continue;
            }
            current.append(value);
            escaped = false;
        }

        cells.add(current.toString().trim());
        return cells;
    }

    private static String leadingWhitespace(String line) {
        int index = 0;
        while (index < line.length()) {
            char value = line.charAt(index);
            if (value != ' ' && value != '\t') {
                break;
            }
            index++;
        }
        return line.substring(0, index);
    }

    private static String padRight(String value, int width) {
        if (value.length() >= width) {
            return value;
        }
        return value + " ".repeat(width - value.length());
    }

    private enum Alignment {
        LEFT,
        RIGHT,
        CENTER,
        NONE
    }

    private record Arguments(Path rootDirectory, boolean writeChanges) {

        private static Arguments parse(String[] args) {
            Path root = Paths.get(".");
            boolean write = false;

            for (String argument : args) {
                if ("--write".equals(argument)) {
                    write = true;
                    continue;
                }
                if ("--help".equals(argument) || "-h".equals(argument)) {
                    printUsageAndExit(0);
                }
                if (argument.startsWith("--")) {
                    System.err.println("Unknown option: " + argument);
                    printUsageAndExit(2);
                }
                root = Paths.get(argument);
            }

            if (!Files.exists(root) || !Files.isDirectory(root)) {
                System.err.println("Root directory does not exist or is not a directory: " + root);
                printUsageAndExit(2);
            }

            return new Arguments(root.toAbsolutePath().normalize(), write);
        }

        private static void printUsageAndExit(int code) {
            String usage = """
                    Usage: java .github/skills/docs-sync/scripts/FormatMarkdownTables.java [--write] [root-directory]

                    Formats Markdown tables to match repository style:
                    - one space around each '|'
                    - padded columns so separators align vertically

                    Options:
                      --write   Apply formatting changes in place.

                    Default mode is check-only and exits with code 1 if formatting changes are needed.
                    """;
            System.out.println(usage);
            System.exit(code);
        }
    }

    private record FormatResult(String text, boolean changed, int changedTables) {}
}
