import * as api from '../api.js';
import { renderBreadcrumb } from '../components/breadcrumb.js';
import { renderMenu } from '../components/menu.js';

export async function render(appEl, path, navigate) {
    appEl.innerHTML = '<div class="page-album"><p style="padding:20px">Loading…</p></div>';
    let data;
    try {
        data = await api.getAlbum(path === '/' ? '' : path);
    } catch (err) {
        appEl.innerHTML = `<div class="page-error"><h1>Error</h1><p>${escapeHtml(err.message)}</p></div>`;
        return;
    }

    const isRoot = path === '/';
    const menuItems = [
        { type: 'action', label: 'Logout', action: 'logout' },
    ];
    if (isRoot) {
        menuItems.push({ type: 'action', label: 'Shutdown server', action: 'shutdown', danger: true });
    }

    const albumTiles = data.albums.map(name => {
        const albumPath = (isRoot ? '' : path) + '/' + name;
        const preview = data.albumPreviews[name];
        const thumbHtml = preview
            ? `<div class="album-thumb-box"><img class="album-thumb" src="${escapeAttr(preview)}" alt="${escapeAttr(name)}" loading="lazy"></div>`
            : `<div class="icon">📁</div>`;
        return `<div class="tile album" data-path="${escapeAttr(albumPath)}">${thumbHtml}<span>${escapeHtml(name)}</span></div>`;
    }).join('');

    const pictureTiles = data.pictures.map(name => {
        const picturePath = (isRoot ? '' : path) + '/' + name;
        const src = '/api/images' + (isRoot ? '' : path) + '/' + encodeURIComponent(name);
        return `<div class="tile picture" data-path="${escapeAttr(picturePath)}">
            <div class="picture-thumb-box"><img class="picture-thumb" src="${escapeAttr(src)}" alt="${escapeAttr(name)}" loading="lazy"></div>
            <span>${escapeHtml(name)}</span>
          </div>`;
    }).join('');

    const gridContent = (albumTiles + pictureTiles) || '<p class="empty">This album is empty.</p>';

    appEl.innerHTML = `
        <div class="page-album">
          <header>
            <div class="left-nav">
              <img class="header-icon" src="/assets/icon.svg" alt="Picture Server">
              ${renderBreadcrumb(path)}
            </div>
            <div class="right-nav">${renderMenu(menuItems)}</div>
          </header>
          <section class="grid" id="album-grid">${gridContent}</section>
          <dialog id="shutdown-dialog" class="confirm-dialog">
            <p>Are you sure you want to shut down the server?</p>
            <div class="confirm-actions">
              <button id="shutdown-cancel">Cancel</button>
              <button id="shutdown-confirm" class="danger">Shut down</button>
            </div>
          </dialog>
        </div>`;

    appEl.addEventListener('click', async e => {
        const tile = e.target.closest('.tile[data-path]');
        if (tile) {
            navigate(tile.dataset.path);
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
            if (btn.dataset.action === 'logout') {
                await api.logout().catch(() => {});
                navigate('/');
            } else if (btn.dataset.action === 'shutdown') {
                appEl.querySelector('#shutdown-dialog')?.showModal();
            }
        }
        if (e.target.id === 'shutdown-cancel') {
            appEl.querySelector('#shutdown-dialog')?.close();
        }
        if (e.target.id === 'shutdown-confirm') {
            appEl.querySelector('#shutdown-dialog')?.close();
            await api.shutdown().catch(() => {});
            appEl.innerHTML = '<div class="page-shutdown"><p>The server is shutting down…</p></div>';
        }
    });
}

function escapeHtml(str) {
    return String(str).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

function escapeAttr(str) {
    return String(str).replace(/&/g, '&amp;').replace(/"/g, '&quot;');
}
