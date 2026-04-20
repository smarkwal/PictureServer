package net.markwalder.pictureserver.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PathSafetyTest {

    @TempDir
    Path rootDir;

    @Test
    void resolveSafePath_returnsRootForNullPath() {
        // Act
        Path result = PathSafety.resolveSafePath(null, rootDir);

        // Assert
        assertThat(result).isEqualTo(rootDir);
    }

    @Test
    void resolveSafePath_returnsRootForEmptyPath() {
        // Act
        Path result = PathSafety.resolveSafePath("", rootDir);

        // Assert
        assertThat(result).isEqualTo(rootDir);
    }

    @Test
    void resolveSafePath_returnsRootForSlashOnlyPath() {
        // Act
        Path result = PathSafety.resolveSafePath("/", rootDir);

        // Assert
        assertThat(result).isEqualTo(rootDir);
    }

    @Test
    void resolveSafePath_resolvesSimplePath() {
        // Act
        Path result = PathSafety.resolveSafePath("/vacation", rootDir);

        // Assert
        assertThat(result).isEqualTo(rootDir.resolve("vacation"));
    }

    @Test
    void resolveSafePath_resolvesNestedPath() {
        // Act
        Path result = PathSafety.resolveSafePath("/vacation/summer/beach.jpg", rootDir);

        // Assert
        assertThat(result).isEqualTo(rootDir.resolve("vacation/summer/beach.jpg"));
    }

    @Test
    void resolveSafePath_resolvesPathWithoutLeadingSlash() {
        // Act
        Path result = PathSafety.resolveSafePath("vacation", rootDir);

        // Assert
        assertThat(result).isEqualTo(rootDir.resolve("vacation"));
    }

    @Test
    void resolveSafePath_allowsInternalDotDotThatStaysInsideRoot() {
        // /vacation/../summer normalizes to summer — still inside root, so safe
        // Act
        Path result = PathSafety.resolveSafePath("/vacation/../summer", rootDir);

        // Assert
        assertThat(result).isEqualTo(rootDir.resolve("summer"));
    }

    @Test
    void resolveSafePath_blocksLeadingDotDotWithoutSlash() {
        // Act & Assert
        assertThatThrownBy(() -> PathSafety.resolveSafePath("../etc/passwd", rootDir))
                .isInstanceOf(SecurityException.class)
                .hasMessage("Path traversal attempt blocked.");
    }

    @Test
    void resolveSafePath_blocksDotDotAfterLeadingSlash() {
        // Act & Assert
        assertThatThrownBy(() -> PathSafety.resolveSafePath("/../etc/passwd", rootDir))
                .isInstanceOf(SecurityException.class)
                .hasMessage("Path traversal attempt blocked.");
    }

    @Test
    void resolveSafePath_blocksUrlEncodedDotDot() {
        // %2e%2e decodes to ".." — must be rejected the same way as a literal ".."
        // Act & Assert
        assertThatThrownBy(() -> PathSafety.resolveSafePath("/%2e%2e/etc/passwd", rootDir))
                .isInstanceOf(SecurityException.class)
                .hasMessage("Path traversal attempt blocked.");
    }

    @Test
    void resolveSafePath_blocksUrlEncodedDotDotWithoutLeadingSlash() {
        // Act & Assert
        assertThatThrownBy(() -> PathSafety.resolveSafePath("%2e%2e/etc/passwd", rootDir))
                .isInstanceOf(SecurityException.class)
                .hasMessage("Path traversal attempt blocked.");
    }

    @Test
    void resolveSafePath_blocksMixedEncodedAndLiteralDotDotTraversal() {
        // /vacation/%2e%2e/../escape decodes to /vacation/../../escape, which normalizes to ../escape
        // Act & Assert
        assertThatThrownBy(() -> PathSafety.resolveSafePath("/vacation/%2e%2e/../escape", rootDir))
                .isInstanceOf(SecurityException.class)
                .hasMessage("Path traversal attempt blocked.");
    }

    @Test
    void resolveSafePath_doubleUrlEncodingIsSafeByDesign() {
        // %252e%252e decodes once to literal "%2e%2e" — treated as a directory name, not ".."
        // Act
        Path result = PathSafety.resolveSafePath("/%252e%252e/secret", rootDir);

        // Assert — path stays inside root (using Path.startsWith to avoid filesystem access)
        assertThat(result.startsWith(rootDir)).isTrue();
    }
}
