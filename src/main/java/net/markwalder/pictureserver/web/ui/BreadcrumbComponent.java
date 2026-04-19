package net.markwalder.pictureserver.web.ui;

import java.util.ArrayList;
import java.util.List;

public final class BreadcrumbComponent implements UiComponent {

    private final String currentPath;

    public BreadcrumbComponent(String currentPath) {
        this.currentPath = currentPath;
    }

    @Override
    public String render() {
        String normalized = (currentPath == null || currentPath.isBlank()) ? "/" : currentPath;

        String[] parts = normalized.split("/");
        List<String> cleanParts = new ArrayList<>();
        for (String part : parts) {
            if (!part.isBlank()) {
                cleanParts.add(part);
            }
        }

        StringBuilder out = new StringBuilder();

        if (cleanParts.isEmpty()) {
            out.append("<span class=\"current\">Home</span>");
            return out.toString();
        }

        out.append("<a href=\"/\">Home</a>");

        StringBuilder runningPath = new StringBuilder();
        for (int i = 0; i < cleanParts.size(); i++) {
            String part = cleanParts.get(i);
            runningPath.append("/").append(part);
            out.append(" / ");
            if (i == cleanParts.size() - 1) {
                out.append("<span class=\"current\">")
                        .append(HtmlEscaper.escape(part))
                        .append("</span>");
            } else {
                out.append("<a href=\"")
                        .append(HtmlEscaper.escape(UrlEncoder.encodePath(runningPath.toString())))
                        .append("\">")
                        .append(HtmlEscaper.escape(part))
                        .append("</a>");
            }
        }

        return out.toString();
    }
}
