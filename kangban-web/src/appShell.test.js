import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

test('AppShell can omit TopBar and exposes a standalone mobile menu trigger', async () => {
  const source = await readFile(new URL('./components/AppShell.jsx', import.meta.url), 'utf8');
  assert.match(source, /showTopBar\s*=\s*true/);
  assert.match(source, /showTopBar\s*\?\s*<TopBar/);
  assert.match(source, /shell-menu-fab/);
  assert.match(source, /app-shell-sidebar-only/);
});

test('the sidebar-only menu trigger follows the shared 820px drawer breakpoint', async () => {
  const css = await readFile(new URL('./styles/global.css', import.meta.url), 'utf8');
  assert.match(css, /\.shell-menu-fab\s*\{[^}]*display:\s*none/s);
  assert.match(css, /@media \(max-width:\s*820px\)[\s\S]*?\.shell-menu-fab\s*\{[^}]*display:\s*grid/s);
  assert.match(css, /@media \(max-width:\s*820px\)[\s\S]*?\.app-shell-sidebar-only \.page-canvas\s*\{[^}]*padding-top:\s*64px/s);
});

test('the shared sidebar supports the three new Stitch destinations', async () => {
  const source = await readFile(new URL('./components/Sidebar.jsx', import.meta.url), 'utf8');
  assert.match(source, /family/);
  assert.match(source, /profile/);
  assert.match(source, /settings/);
});
