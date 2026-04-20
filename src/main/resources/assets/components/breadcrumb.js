export function renderBreadcrumb(path, currentLabelOverride = null) {
    const segments = path.split('/').filter(Boolean);
    if (segments.length === 0) {
        return '<span class="crumbs"><span class="current">Home</span></span>';
    }
    const parts = [];
    let accumulated = '';
    for (let i = 0; i < segments.length; i++) {
        accumulated += '/' + segments[i];
        const label = decodeLabel(segments[i]);
        if (i < segments.length - 1) {
            const href = accumulated;
            parts.push(`<a href="${escapeAttr(href)}" data-path="${escapeAttr(href)}">${escapeHtml(label)}</a>`);
        } else {
            const currentLabel = currentLabelOverride ?? label;
            parts.push(`<span class="current">${escapeHtml(currentLabel)}</span>`);
        }
    }
    const homeLink = `<a href="/" data-path="/">Home</a>`;
    return `<span class="crumbs">${homeLink} / ${parts.join(' / ')}</span>`;
}

function decodeLabel(segment) {
    try {
        return decodeURIComponent(segment);
    } catch {
        return segment;
    }
}

function escapeHtml(str) {
    return str.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

function escapeAttr(str) {
    return str.replace(/&/g, '&amp;').replace(/"/g, '&quot;');
}
