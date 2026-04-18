package net.markwalder.pictureserver.web.ui;

public final class MenuDialogItemComponent implements UiComponent {

    private final String label;
    private final String dialogId;
    private final boolean danger;

    public MenuDialogItemComponent(String label, String dialogId, boolean danger) {
        this.label = label;
        this.dialogId = dialogId;
        this.danger = danger;
    }

    @Override
    public String render() {
        return """
                <button type=\"button\" class=\"menu-item %s\" onclick=\"this.closest('details').removeAttribute('open'); document.getElementById('%s').showModal()\">%s</button>
                """.formatted(danger ? "danger" : "", HtmlEscaper.escape(dialogId), HtmlEscaper.escape(label));
    }
}
