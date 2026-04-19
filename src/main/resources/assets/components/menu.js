export function renderMenu(items) {
    const itemsHtml = items.map(item => {
        if (item.type === 'link') {
            return `<a class="menu-item" href="${escapeAttr(item.href)}" data-path="${escapeAttr(item.href)}">${escapeHtml(item.label)}</a>`;
        }
        if (item.type === 'action') {
            const danger = item.danger ? ' danger' : '';
            return `<button class="menu-item${danger}" data-action="${escapeAttr(item.action)}">${escapeHtml(item.label)}</button>`;
        }
        return '';
    }).join('');

    return `
        <details class="user-menu">
          <summary class="menu-button">☰</summary>
          <div class="menu-panel">${itemsHtml}</div>
        </details>`;
}

function escapeHtml(str) {
    return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

function escapeAttr(str) {
    return str.replace(/&/g, '&amp;').replace(/"/g, '&quot;');
}
