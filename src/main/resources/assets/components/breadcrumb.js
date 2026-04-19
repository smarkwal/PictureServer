export function renderBreadcrumb(path) {
    const segments = path.split('/').filter(Boolean);
    if (segments.length === 0) {
        return '<span class="crumbs"><span class="current">Home</span></span>';
    }
    const parts = [];
    let accumulated = '';
    for (let i = 0; i < segments.length; i++) {
        accumulated += '/' + segments[i];
        const label = segments[i];
        if (i < segments.length - 1) {
            const href = accumulated;
            parts.push(`<a href="${escapeAttr(href)}" data-path="${escapeAttr(href)}">${escapeHtml(label)}</a>`);
        } else {
            parts.push(`<span class="current">${escapeHtml(label)}</span>`);
        }
    }
    const homeLink = `<a href="/" data-path="/">Home</a>`;
    return `<span class="crumbs">${homeLink} / ${parts.join(' / ')}</span>`;
}

function escapeHtml(str) {
    return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

function escapeAttr(str) {
    return str.replace(/&/g, '&amp;').replace(/"/g, '&quot;');
}
