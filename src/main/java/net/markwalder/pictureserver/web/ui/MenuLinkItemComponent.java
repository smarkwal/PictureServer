package net.markwalder.pictureserver.web.ui;

public final class MenuLinkItemComponent implements UiComponent {

    private final String label;
    private final String href;

    public MenuLinkItemComponent(String label, String href) {
        this.label = label;
        this.href = href;
    }

    @Override
    public String render() {
        return """
                <a class=\"menu-item\" href=\"%s\" onclick=\"this.closest('details').removeAttribute('open')\">%s</a>
                """.formatted(HtmlEscaper.escape(href), HtmlEscaper.escape(label));
    }
}
