import * as api from '../api.js';
import { renderBreadcrumb } from '../components/breadcrumb.js';
import { renderMenu } from '../components/menu.js';

let viewState = null;
let updateVersion = 0;

export async function render(appEl, path, navigate, showLogin, signal) {
    appEl.innerHTML = '<div class="page-picture"><p style="padding:20px">Loading…</p></div>';
    let data;
    try {
        data = await api.getPicture(path);
    } catch (err) {
        if (err.status === 401) { showLogin(); return; }
        appEl.innerHTML = `<div class="page-error"><h1>Error</h1><p>${escapeHtml(err.message)}</p></div>`;
        return;
    }

    updateVersion += 1;

    const menuItems = [
        { type: 'action', label: 'Delete picture', action: 'delete', danger: true },
        { type: 'action', label: 'Logout', action: 'logout' },
    ];

    const parentPath = path.substring(0, path.lastIndexOf('/')) || '/';

    const sidebarHtml = data.siblings.map(sibPath => {
        const isCurrent = sibPath === path;
        const imgSrc = '/api/images' + encodeURIPath(sibPath);
        const imgClass = 'sidebar-thumb-image' + (isCurrent ? ' sidebar-thumb-image-current' : '');
        const thumbClass = 'sidebar-thumb' + (isCurrent ? ' sidebar-thumb-current' : '');
        return `<span class="${thumbClass}" data-path="${escapeAttr(sibPath)}">
            <img class="${imgClass}" src="${escapeAttr(imgSrc)}" alt="" loading="lazy">
          </span>`;
    }).join('');

        appEl.innerHTML = `
                <div class="page-picture">
                    <header>
                        <div class="left-nav">
                            <img class="header-icon" src="/assets/icon.svg" alt="Picture Server">
                            <div id="picture-breadcrumb">${renderBreadcrumb(path, data.name)}</div>
                        </div>
                        <div class="right-nav">${renderMenu(menuItems)}</div>
                    </header>
                    <div class="picture-layout">
                        <div class="picture-sidebar" id="sidebar">${sidebarHtml}</div>
                        <main>
                            <div class="picture-stage">
                                <img src="${escapeAttr(data.src)}" alt="${escapeHtml(data.name)}">
                            </div>
                        </main>
                    </div>
                    <dialog id="delete-dialog" class="confirm-dialog">
                        <p>Move this picture to the trash?</p>
                        <div class="confirm-actions">
                            <button id="delete-cancel">Cancel</button>
                            <button id="delete-confirm" class="danger">Delete</button>
                        </div>
                    </dialog>
                </div>`;

    viewState = {
        appEl,
        navigate,
        showLogin,
        signal,
        path,
        pictureName: data.name,
        parentPath,
        siblings: data.siblings,
    };

    const currentThumb = appEl.querySelector('.sidebar-thumb-current');
    if (currentThumb) {
        currentThumb.scrollIntoView({ block: 'nearest' });
    }

    appEl.addEventListener('click', async e => {
        if (!viewState || viewState.signal.aborted) {
            return;
        }

        const thumb = e.target.closest('.sidebar-thumb[data-path]');
        if (thumb) {
            viewState.navigate(thumb.dataset.path);
            return;
        }
        const link = e.target.closest('a[data-path]');
        if (link) {
            e.preventDefault();
            viewState.navigate(link.dataset.path);
            return;
        }
        const btn = e.target.closest('button[data-action]');
        if (btn) {
            const details = appEl.querySelector('details.user-menu');
            if (details) details.removeAttribute('open');
            if (btn.dataset.action === 'delete') {
                appEl.querySelector('#delete-dialog')?.showModal();
            } else if (btn.dataset.action === 'logout') {
                await api.logout().catch(() => {});
                viewState.navigate('/');
            }
        }
        if (e.target.id === 'delete-cancel') {
            appEl.querySelector('#delete-dialog')?.close();
        }
        if (e.target.id === 'delete-confirm') {
            appEl.querySelector('#delete-dialog')?.close();
            try {
                await api.deletePicture(viewState.path);
                viewState.navigate(viewState.parentPath);
            } catch (err) {
                alert('Could not delete: ' + err.message);
            }
        }
    }, { signal });

    document.addEventListener('keydown', e => {
        if (!viewState || viewState.signal.aborted) {
            return;
        }

        const idx = viewState.siblings.indexOf(viewState.path);
        if (e.key === 'ArrowLeft' && idx > 0) viewState.navigate(viewState.siblings[idx - 1]);
        if (e.key === 'ArrowRight' && idx < viewState.siblings.length - 1) viewState.navigate(viewState.siblings[idx + 1]);
    }, { signal });
}

export async function update(path) {
    if (!viewState || !viewState.appEl.isConnected || viewState.signal.aborted) {
        return;
    }

    const version = ++updateVersion;
    let data;
    try {
        data = await api.getPicture(path);
    } catch (err) {
        if (version !== updateVersion || !viewState || viewState.signal.aborted) {
            return;
        }

        if (err.status === 401) {
            viewState.showLogin();
            return;
        }

        alert('Could not load picture: ' + err.message);
        return;
    }

    if (version !== updateVersion || !viewState || viewState.signal.aborted) {
        return;
    }

    const appEl = viewState.appEl;
    const sidebarEl = appEl.querySelector('#sidebar');
    const stageImageEl = appEl.querySelector('.picture-stage img');
    const breadcrumbEl = appEl.querySelector('#picture-breadcrumb');
    if (!sidebarEl || !stageImageEl || !breadcrumbEl) {
        return;
    }

    const parentPath = path.substring(0, path.lastIndexOf('/')) || '/';

    viewState.path = path;
    viewState.pictureName = data.name;
    viewState.parentPath = parentPath;
    viewState.siblings = data.siblings;

    stageImageEl.src = data.src;
    stageImageEl.alt = data.name;
    breadcrumbEl.innerHTML = renderBreadcrumb(path, data.name);

    sidebarEl.innerHTML = renderSidebar(data.siblings, path);
    const currentThumb = sidebarEl.querySelector('.sidebar-thumb-current');
    if (currentThumb) {
        currentThumb.scrollIntoView({ block: 'nearest' });
    }
}

function renderSidebar(siblings, currentPath) {
    return siblings.map(sibPath => {
        const isCurrent = sibPath === currentPath;
        const imgSrc = '/api/images' + encodeURIPath(sibPath);
        const imgClass = 'sidebar-thumb-image' + (isCurrent ? ' sidebar-thumb-image-current' : '');
        const thumbClass = 'sidebar-thumb' + (isCurrent ? ' sidebar-thumb-current' : '');
        return `<span class="${thumbClass}" data-path="${escapeAttr(sibPath)}">
            <img class="${imgClass}" src="${escapeAttr(imgSrc)}" alt="" loading="lazy">
          </span>`;
    }).join('');
}

function encodeURIPath(path) {
    return path.split('/').map(seg => seg ? encodeURIComponent(seg) : '').join('/');
}

function escapeHtml(str) {
    return String(str).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

function escapeAttr(str) {
    return String(str).replace(/&/g, '&amp;').replace(/"/g, '&quot;');
}
