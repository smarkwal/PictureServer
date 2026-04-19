import * as loginView from './views/login.js';
import * as albumView from './views/album.js';
import * as pictureView from './views/picture.js';

const IMAGE_EXTENSIONS = ['.jpg', '.jpeg', '.png', '.gif', '.webp', '.bmp'];

let appEl;
let pendingPath = null;

export function init(el) {
    appEl = el;
    window.addEventListener('popstate', () => renderView(window.location.pathname));
    start();
}

async function start() {
    const { checkSession } = await import('./api.js');
    pendingPath = window.location.pathname;
    try {
        const data = await checkSession();
        if (data.authenticated) {
            renderView(pendingPath);
        } else {
            showLogin();
        }
    } catch {
        showLogin();
    }
}

function showLogin() {
    loginView.render(appEl, () => {
        const target = pendingPath && pendingPath !== '/' ? pendingPath : '/';
        pendingPath = null;
        navigate(target);
    });
}

export function navigate(path, pushState = true) {
    if (pushState) {
        window.history.pushState({}, '', path);
    }
    renderView(path);
}

function renderView(path) {
    if (!path || path === '/') {
        albumView.render(appEl, '/', navigate);
    } else if (isImagePath(path)) {
        pictureView.render(appEl, path, navigate);
    } else {
        albumView.render(appEl, path, navigate);
    }
}

function isImagePath(path) {
    const lower = path.toLowerCase();
    return IMAGE_EXTENSIONS.some(ext => lower.endsWith(ext));
}
