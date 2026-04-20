import * as api from '../api.js';
import { renderTitleBar } from '../components/page-template.js';

export function render(template, onSuccess) {
    template.setTitleBar(renderTitleBar({
        navigationHtml: '<span class="title-label">Picture Server</span>',
    }));

    template.setCenter(`
        <div class="page-login-content">
          <div class="card">
            <form id="login-form">
              <div id="login-error" class="error" style="display:none"></div>
              <label for="username">Username</label>
              <input id="username" type="text" name="username" autocomplete="username" required>
              <label for="password">Password</label>
              <input id="password" type="password" name="password" autocomplete="current-password" required>
              <button type="submit" id="login-btn">Sign in</button>
            </form>
          </div>
        </div>
    `, 'page-login-center');

    const form = template.centerEl.querySelector('#login-form');
    const errorEl = template.centerEl.querySelector('#login-error');
    const btn = template.centerEl.querySelector('#login-btn');

    form.addEventListener('submit', async e => {
        e.preventDefault();
        errorEl.style.display = 'none';
        btn.disabled = true;
        btn.textContent = 'Signing in…';
        try {
            await api.login(
                form.querySelector('#username').value,
                form.querySelector('#password').value
            );
            onSuccess();
        } catch (err) {
            errorEl.textContent = err.status === 401 ? 'Username or password is incorrect.' : 'Login failed. Please try again.';
            errorEl.style.display = 'block';
            btn.disabled = false;
            btn.textContent = 'Sign in';
        }
    });
}
