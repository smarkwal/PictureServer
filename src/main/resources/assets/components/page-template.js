export function createPageTemplate(appEl) {
    appEl.innerHTML = `
        <div class="app-shell" id="app-shell">
          <header class="title-bar" id="title-bar"></header>
          <section class="page-center" id="page-center"></section>
        </div>`;

    const titleBarEl = appEl.querySelector('#title-bar');
    const centerEl = appEl.querySelector('#page-center');

    return {
        rootEl: appEl.querySelector('#app-shell'),
        titleBarEl,
        centerEl,
        setTitleBar(html) {
            titleBarEl.innerHTML = html;
        },
        setCenter(html, centerClass = '') {
            centerEl.className = centerClass ? `page-center ${centerClass}` : 'page-center';
            centerEl.innerHTML = html;
        },
    };
}

export function renderTitleBar({ navigationHtml, actionsHtml = '', menuHtml = '' }) {
    const actionsSlot = actionsHtml ? `<div class="title-actions">${actionsHtml}</div>` : '';
    const menuSlot = menuHtml ? `<div class="title-menu">${menuHtml}</div>` : '';

    return `
        <div class="title-left">
          <img class="title-icon" src="/assets/icon.svg" alt="Picture Server">
          <div class="title-navigation">${navigationHtml}</div>
        </div>
        ${actionsSlot}
        ${menuSlot}`;
}
