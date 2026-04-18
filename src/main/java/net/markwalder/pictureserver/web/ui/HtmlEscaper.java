package net.markwalder.pictureserver.web.ui;

public final class HtmlEscaper {

    private HtmlEscaper() {
    }

    public static String escape(String value) {
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
