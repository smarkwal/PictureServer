package net.markwalder.pictureserver.web.ui;

import java.util.List;

public final class UserMenuComponent implements UiComponent {

    private final List<UiComponent> menuItems;

    public UserMenuComponent(List<UiComponent> menuItems) {
        this.menuItems = menuItems;
    }

    @Override
    public String render() {
        StringBuilder itemsHtml = new StringBuilder();
        for (UiComponent menuItem : menuItems) {
            itemsHtml.append(menuItem.render());
        }

        return """
                <details class=\"user-menu\">
                  <summary class=\"menu-button\" aria-label=\"Open user menu\">☰</summary>
                  <nav class=\"menu-panel\">
                    %s
                  </nav>
                </details>
                """.formatted(itemsHtml);
    }
}
