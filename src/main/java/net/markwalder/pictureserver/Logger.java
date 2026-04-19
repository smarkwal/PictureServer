package net.markwalder.pictureserver;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class Logger {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private Logger() {
    }

    public static void log(String message) {
        System.out.println(LocalDateTime.now().format(FORMATTER) + " " + message);
    }

    public static void log(String format, Object... args) {
        log(String.format(format, args));
    }
}
