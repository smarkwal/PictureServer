import { init } from './router.js';

// Eagerly import all views and components so they are fetched on startup
import './views/login.js';
import './views/album.js';
import './views/picture.js';
import './components/breadcrumb.js';
import './components/menu.js';
import './api.js';

const appEl = document.getElementById('app');
init(appEl);
