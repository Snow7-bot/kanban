import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';
import {
  clearPendingRoute,
  consumePendingRoute,
  isProtectedPage,
  rememberPendingRoute,
} from './authNavigation.js';

const source = (file) => readFile(new URL(file, import.meta.url), 'utf8');

test('guest routes keep home public and protect personal health pages', () => {
  assert.equal(isProtectedPage('home'), false);
  assert.equal(isProtectedPage('login'), false);
  assert.equal(isProtectedPage('register'), false);
  assert.equal(isProtectedPage('consultation'), true);
  assert.equal(isProtectedPage('records'), true);
  assert.equal(isProtectedPage('medications'), true);
});

test('pending destination survives login navigation and is consumed once', () => {
  const values = new Map();
  globalThis.sessionStorage = {
    getItem: (key) => values.get(key) ?? null,
    setItem: (key, value) => values.set(key, value),
    removeItem: (key) => values.delete(key),
  };

  rememberPendingRoute('record-detail', { id: 18 });
  assert.deepEqual(consumePendingRoute(), {
    pageId: 'record-detail',
    params: { id: 18 },
  });
  assert.equal(consumePendingRoute(), null);

  rememberPendingRoute('consultation');
  clearPendingRoute();
  assert.equal(consumePendingRoute(), null);
  delete globalThis.sessionStorage;
});

test('guest home suppresses private requests and renders login placeholders', async () => {
  const home = await source('./pages/HomePage.jsx');
  assert.match(home, /if \(!isAuthenticated\)[\s\S]*?setDashboard\(\{\}\)[\s\S]*?return undefined/);
  assert.match(home, /value: '--'/);
  assert.match(home, /登录后查看/);
  assert.match(home, /\}, \[isAuthenticated\]\)/);
});

test('protected navigation uses a centered login prompt and preserves existing auth pages', async () => {
  const [app, login, sidebar, authContext, css] = await Promise.all([
    source('./App.jsx'),
    source('./pages/LoginPage.jsx'),
    source('./components/Sidebar.jsx'),
    source('./context/AuthContext.jsx'),
    source('./styles/global.css'),
  ]);

  assert.match(app, /登录后即可使用此功能/);
  assert.match(app, /rememberPendingRoute\(nextPage, params\)/);
  assert.match(app, /consumePendingRoute\(\)/);
  assert.match(app, /effectivePageId/);
  assert.match(login, /onLoginSuccess/);
  assert.match(login, /立即注册/);
  assert.match(sidebar, /未登录/);
  assert.match(sidebar, /登录 \/ 注册/);
  assert.match(sidebar, /onNavigate\(isAuthenticated \? 'profile' : 'login'\)/);
  assert.match(authContext, /window\.location\.hash = '#home'/);
  assert.doesNotMatch(authContext, /window\.location\.hash = '#login'/);
  assert.match(css, /\.login-required-backdrop/);
  assert.match(css, /\.login-required-dialog/);
});
