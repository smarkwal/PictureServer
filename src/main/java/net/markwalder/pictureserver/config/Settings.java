package net.markwalder.pictureserver.config;

import java.nio.file.Path;

public record Settings(Path rootDirectory, int port, String password) {
}
