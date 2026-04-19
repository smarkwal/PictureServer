import * as api from '../api.js';
import { renderBreadcrumb } from '../components/breadcrumb.js';
import { renderMenu } from '../components/menu.js';

export async function render(appEl, path, navigate) {
    appEl.innerHTML = '<div class="page-picture"><p style="padding:20px">Loading…</p></div>';
    let data;
    try {
        data = await api.getPicture(path);
    } catch (err) {
        appEl.innerHTML = `<div class="page-error"><h1>Error</h1><p>${escapeHtml(err.message)}</p></div>`;
        return;
    }

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
              ${renderBreadcrumb(path)}
            </div>
            <div class="right-nav">${renderMenu(menuItems)}</div>
          </header>
          <div class="picture-layout">
            <div class="picture-sidebar" id="sidebar">${sidebarHtml}</div>
            <main>
              <div class="picture-stage">
                <img src="${escapeAttr(data.src)}" alt="${escapeHtml(path.split('/').pop())}">
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

    const currentThumb = appEl.querySelector('.sidebar-thumb-current');
    if (currentThumb) {
        currentThumb.scrollIntoView({ block: 'nearest' });
    }

    appEl.addEventListener('click', async e => {
        const thumb = e.target.closest('.sidebar-thumb[data-path]');
        if (thumb) {
            navigate(thumb.dataset.path);
            return;
        }
        const link = e.target.closest('a[data-path]');
        if (link) {
            e.preventDefault();
            navigate(link.dataset.path);
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
                navigate('/');
            }
        }
        if (e.target.id === 'delete-cancel') {
            appEl.querySelector('#delete-dialog')?.close();
        }
        if (e.target.id === 'delete-confirm') {
            appEl.querySelector('#delete-dialog')?.close();
            try {
                await api.deletePicture(path);
                navigate(parentPath);
            } catch (err) {
                alert('Could not delete: ' + err.message);
            }
        }
    });

    document.addEventListener('keydown', onKey);
    function onKey(e) {
        if (!appEl.isConnected) {
            document.removeEventListener('keydown', onKey);
            return;
        }
        const idx = data.siblings.indexOf(path);
        if (e.key === 'ArrowLeft' && idx > 0) navigate(data.siblings[idx - 1]);
        if (e.key === 'ArrowRight' && idx < data.siblings.length - 1) navigate(data.siblings[idx + 1]);
    }
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
