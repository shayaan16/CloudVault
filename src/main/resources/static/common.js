'use strict';
let csrf;
async function request(url, options = {}) {
  const method = options.method || 'GET';
  const headers = new Headers(options.headers || {});
  if (!['GET', 'HEAD'].includes(method)) {
    if (!csrf) csrf = await request('/api/auth/csrf');
    headers.set(csrf.header, csrf.token);
  }
  if (options.body && !(options.body instanceof FormData) && !(options.body instanceof URLSearchParams)) {
    headers.set('Content-Type', 'application/json');
    options.body = JSON.stringify(options.body);
  }
  const res = await fetch(url, {...options, headers, credentials:'same-origin', cache:'no-store'});
  if (!res.ok) {
    let message = `Request failed (${res.status})`;
    try { const data = await res.json(); message = data.message || message; } catch (_) {}
    if (res.status === 401 && ['/','/index.html'].includes(location.pathname)) location.assign('/auth.html');
    if (res.status === 403) csrf = null;
    throw new Error(message);
  }
  return res.headers.get('content-type')?.includes('application/json') ? res.json() : null;
}
const $ = (selector, root = document) => root.querySelector(selector);
const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];
function el(tag, text, className) {
  const node = document.createElement(tag);
  if (text !== undefined && text !== null) node.textContent = text;
  if (className) node.className = className;
  return node;
}
function size(bytes) {
  if (!bytes) return '0 B';
  const unit = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), 3);
  return `${Number((bytes / 1024 ** unit).toFixed(unit ? 1 : 0))} ${['B','KB','MB','GB'][unit]}`;
}
function date(value) { return value ? new Date(value).toLocaleDateString('en-GB') : '—'; }
function button(text, action, className = 'btn-light') {
  const b = el('button', text, `button ${className}`); b.type = 'button'; b.addEventListener('click', action); return b;
}
function notify(message, error = false) {
  const box = $('#notice'); if (!box) return;
  box.textContent = message; box.classList.toggle('error', error); box.hidden = false;
  clearTimeout(notify.timer); notify.timer = setTimeout(() => box.hidden = true, 6500);
}
