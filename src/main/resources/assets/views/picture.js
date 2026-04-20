import * as api from '../api.js';
import { renderBreadcrumb } from '../components/breadcrumb.js';
import { renderMenu } from '../components/menu.js';
import { renderTitleBar } from '../components/page-template.js';

let viewState = null;
let updateVersion = 0;

export async function render(template, path, navigate, showLogin, signal) {
    template.setTitleBar(renderTitleBar({
        navigationHtml: renderBreadcrumb(path),
    }));
    template.setCenter('<p class="page-loading">Loading...</p>', 'page-picture-center');

    let data;
    try {
        data = await api.getPicture(path);
    } catch (err) {
        if (err.status === 401) {
            showLogin();
            return;
        }

        template.setCenter(`<div class="page-error"><h1>Error</h1><p>${escapeHtml(err.message)}</p></div>`, 'page-picture-center');
        return;
    }

    updateVersion += 1;

    const menuItems = [
        { type: 'action', label: 'Delete picture', action: 'delete', danger: true },
        { type: 'action', label: 'Logout', action: 'logout' },
    ];

    renderPictureLayout(template, path, data, menuItems);

    const parentPath = path.substring(0, path.lastIndexOf('/')) || '/';
    viewState = {
        template,
        navigate,
        showLogin,
        signal,
        path,
        pictureName: data.name,
        parentPath,
        siblings: data.siblings,
        menuItems,
    };

    const currentThumb = template.centerEl.querySelector('.sidebar-thumb-current');
    if (currentThumb) {
        currentThumb.scrollIntoView({ block: 'nearest' });
    }

    template.rootEl.addEventListener('click', async e => {
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
            const details = viewState.template.titleBarEl.querySelector('details.user-menu');
            if (details) {
                details.removeAttribute('open');
            }

            if (btn.dataset.action === 'delete') {
                viewState.template.centerEl.querySelector('#delete-dialog')?.showModal();
            } else if (btn.dataset.action === 'logout') {
                await api.logout().catch(() => {});
                viewState.navigate('/');
            }
        }

        if (e.target.id === 'delete-cancel') {
            viewState.template.centerEl.querySelector('#delete-dialog')?.close();
        }

        if (e.target.id === 'delete-confirm') {
            viewState.template.centerEl.querySelector('#delete-dialog')?.close();
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
        if (e.key === 'ArrowLeft' && idx > 0) {
            viewState.navigate(viewState.siblings[idx - 1]);
        }
        if (e.key === 'ArrowRight' && idx < viewState.siblings.length - 1) {
            viewState.navigate(viewState.siblings[idx + 1]);
        }
    }, { signal });
}

export async function update(path) {
    if (!viewState || !viewState.template.rootEl.isConnected || viewState.signal.aborted) {
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

    const parentPath = path.substring(0, path.lastIndexOf('/')) || '/';
    viewState.path = path;
    viewState.pictureName = data.name;
    viewState.parentPath = parentPath;
    viewState.siblings = data.siblings;

    renderPictureLayout(viewState.template, path, data, viewState.menuItems);

    const currentThumb = viewState.template.centerEl.querySelector('.sidebar-thumb-current');
    if (currentThumb) {
        currentThumb.scrollIntoView({ block: 'nearest' });
    }
}

function renderPictureLayout(template, path, data, menuItems) {
    template.setTitleBar(renderTitleBar({
        navigationHtml: renderBreadcrumb(path, data.name),
        menuHtml: renderMenu(menuItems),
    }));

    template.setCenter(`
        <div class="page-picture-content">
          <div class="picture-layout">
            <div class="picture-sidebar" id="sidebar">${renderSidebar(data.siblings, path)}</div>
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
        </div>
    `, 'page-picture-center');
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
