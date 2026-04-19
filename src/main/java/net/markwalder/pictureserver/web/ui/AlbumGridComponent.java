package net.markwalder.pictureserver.web.ui;

import java.util.List;

public final class AlbumGridComponent implements UiComponent {

    private final String currentPath;
    private final List<String> albums;
    private final List<String> pictures;

    public AlbumGridComponent(String currentPath, List<String> albums, List<String> pictures) {
        this.currentPath = currentPath;
        this.albums = albums;
        this.pictures = pictures;
    }

    @Override
    public String render() {
        StringBuilder grid = new StringBuilder();
        for (String album : albums) {
            String href = joinPaths(currentPath, album);
            grid.append("<a class=\"tile album\" href=\"")
                    .append(HtmlEscaper.escape(UrlEncoder.encodePath(href)))
                    .append("\"><span class=\"icon\">📁</span><span>")
                    .append(HtmlEscaper.escape(album))
                    .append("</span></a>");
        }

        for (String picture : pictures) {
            String filePath = joinPaths(currentPath, picture);
            String href = filePath + ".html";
            grid.append("<a class=\"tile picture\" href=\"")
                    .append(HtmlEscaper.escape(UrlEncoder.encodePath(href)))
                    .append("\"><div class=\"picture-thumb-box\"><img class=\"picture-thumb\" src=\"")
                    .append(HtmlEscaper.escape(UrlEncoder.encodePath(filePath)))
                    .append("\" alt=\"")
                    .append(HtmlEscaper.escape(picture))
                    .append("\"></div></a>");
        }

        if (grid.length() == 0) {
            return "<p class=\"empty\">No albums or pictures here.</p>";
        }

        return "<section class=\"grid\">" + grid + "</section>";
    }

    private static String joinPaths(String basePath, String entry) {
        if (basePath == null || basePath.isBlank() || "/".equals(basePath)) {
            return "/" + entry;
        }
        return basePath + "/" + entry;
    }
}
