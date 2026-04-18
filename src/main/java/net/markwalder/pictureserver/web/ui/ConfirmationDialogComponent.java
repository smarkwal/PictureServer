package net.markwalder.pictureserver.web.ui;

public final class ConfirmationDialogComponent implements UiComponent {

    private final String dialogId;
    private final String message;
    private final String action;
    private final String hiddenName;
    private final String hiddenValue;
    private final String confirmLabel;
    private final boolean danger;

    public ConfirmationDialogComponent(
            String dialogId,
            String message,
            String action,
            String hiddenName,
            String hiddenValue,
            String confirmLabel,
            boolean danger) {
        this.dialogId = dialogId;
        this.message = message;
        this.action = action;
        this.hiddenName = hiddenName;
        this.hiddenValue = hiddenValue;
        this.confirmLabel = confirmLabel;
        this.danger = danger;
    }

    @Override
    public String render() {
        String hiddenInput = (hiddenName == null || hiddenName.isBlank())
                ? ""
                : "<input type=\"hidden\" name=\"" + HtmlEscaper.escape(hiddenName) + "\" value=\""
                        + HtmlEscaper.escape(hiddenValue == null ? "" : hiddenValue)
                        + "\">";

        return """
                <dialog id=\"%s\" class=\"confirm-dialog\">
                  <form method=\"post\" action=\"%s\" class=\"confirm-form\">
                    <p>%s</p>
                    %s
                    <div class=\"confirm-actions\">
                      <button type=\"submit\" class=\"%s\">%s</button>
                      <button type=\"submit\" formmethod=\"dialog\">Cancel</button>
                    </div>
                  </form>
                </dialog>
                """.formatted(
                HtmlEscaper.escape(dialogId),
                HtmlEscaper.escape(action),
                HtmlEscaper.escape(message),
                hiddenInput,
                danger ? "danger" : "",
                HtmlEscaper.escape(confirmLabel));
    }
}
