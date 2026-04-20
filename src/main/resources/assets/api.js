class ApiError extends Error {
    constructor(status, message) {
        super(message);
        this.status = status;
    }
}

async function request(method, path, body) {
    const options = { method, headers: {} };
    if (body !== undefined) {
        options.headers['Content-Type'] = 'application/json';
        options.body = JSON.stringify(body);
    }
    const res = await fetch(path, options);
    if (res.status === 204) return null;
    const data = await res.json().catch(() => ({}));
    if (!res.ok) throw new ApiError(res.status, data.error || res.statusText);
    return data;
}

export async function checkSession() {
    return request('GET', '/api/session');
}

export async function login(username, password) {
    return request('POST', '/api/login', { username, password });
}

export async function logout() {
    return request('POST', '/api/logout');
}

export async function getAlbum(path) {
    const encoded = encodeURIPath(path);
    return request('GET', '/api/albums' + encoded);
}

export async function getPicture(path) {
    const encoded = encodeURIPath(path);
    return request('GET', '/api/pictures' + encoded);
}

export async function deletePicture(path) {
    const encoded = encodeURIPath(path);
    return request('DELETE', '/api/pictures' + encoded);
}

export async function shutdown() {
    return request('POST', '/api/shutdown');
}

function encodeURIPath(path) {
    return path.split('/').map(seg => seg ? encodeURIComponent(seg) : '').join('/');
}
