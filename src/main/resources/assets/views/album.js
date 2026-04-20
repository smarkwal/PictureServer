import * as api from '../api.js';
import { renderBreadcrumb } from '../components/breadcrumb.js';
import { renderMenu } from '../components/menu.js';
import { renderTitleBar } from '../components/page-template.js';

export async function render(template, path, navigate, showLogin, signal) {
    template.setTitleBar(renderTitleBar({
        navigationHtml: renderBreadcrumb(path),
    }));
    template.setCenter('<p class="page-loading">Loading...</p>', 'page-album-center');

    let data;
    try {
        data = await api.getAlbum(path === '/' ? '' : path);
    } catch (err) {
        if (err.status === 401) { showLogin(); return; }
        template.setCenter(`<div class="page-error"><h1>Error</h1><p>${escapeHtml(err.message)}</p></div>`, 'page-album-center');
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
          </div>`;
    }).join('');

    const gridContent = (albumTiles + pictureTiles) || '<p class="empty">This album is empty.</p>';

        template.setTitleBar(renderTitleBar({
                navigationHtml: renderBreadcrumb(path),
                menuHtml: renderMenu(menuItems),
        }));

        template.setCenter(`
                <div class="page-album-content">
                    <section class="grid" id="album-grid">${gridContent}</section>
                    <dialog id="shutdown-dialog" class="confirm-dialog">
                        <p>Are you sure you want to shut down the server?</p>
                        <div class="confirm-actions">
                            <button id="shutdown-cancel">Cancel</button>
                            <button id="shutdown-confirm" class="danger">Shut down</button>
                        </div>
                    </dialog>
                </div>
        `, 'page-album-center');

        template.rootEl.addEventListener('click', async e => {
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
            const details = template.titleBarEl.querySelector('details.user-menu');
            if (details) details.removeAttribute('open');
            if (btn.dataset.action === 'logout') {
                await api.logout().catch(() => {});
                navigate('/');
            } else if (btn.dataset.action === 'shutdown') {
                template.centerEl.querySelector('#shutdown-dialog')?.showModal();
            }
        }
        if (e.target.id === 'shutdown-cancel') {
            template.centerEl.querySelector('#shutdown-dialog')?.close();
        }
        if (e.target.id === 'shutdown-confirm') {
            template.centerEl.querySelector('#shutdown-dialog')?.close();
            await api.shutdown().catch(() => {});
            template.setCenter('<div class="page-shutdown"><p>The server is shutting down...</p></div>', 'page-album-center');
        }
    }, { signal });
}

function escapeHtml(str) {
    return String(str).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

function escapeAttr(str) {
    return String(str).replace(/&/g, '&amp;').replace(/"/g, '&quot;');
}
