import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const cssUrl = new URL('./styles/home.css', import.meta.url);

test('home right rail never becomes a three-column strip', async () => {
  const css = await readFile(cssUrl, 'utf8');
  assert.doesNotMatch(css, /home-right-rail[^}]*repeat\(3/s);
  assert.match(css, /\.home-right-rail\s*\{[^}]*grid-template-columns:\s*1fr/s);
});

test('home only stacks the right rail at the mobile breakpoint', async () => {
  const css = await readFile(cssUrl, 'utf8');
  assert.match(css, /@media \(max-width:\s*820px\)[\s\S]*?\.home-stage\s*\{[^}]*flex-wrap:\s*wrap/s);
});

test('home uses the shared shell sidebar instead of a page-specific sidebar', async () => {
  const page = await readFile(new URL('./pages/HomePage.jsx', import.meta.url), 'utf8');
  const shellMode = await readFile(new URL('./shellMode.js', import.meta.url), 'utf8');
  assert.doesNotMatch(page, /home-sidebar|HomeSidebar|homeNav/);
  assert.match(shellMode, /return 'app'/);
});

test('home desktop columns and cards follow the approved reference geometry', async () => {
  const css = await readFile(cssUrl, 'utf8');
  assert.match(css, /\.home-stage\s*\{[^}]*min-height:\s*100vh/s);
  assert.doesNotMatch(css, /\.home-stage\s*\{[^}]*margin-left:/s);
  assert.match(css, /\.home-right-rail\s*\{[^}]*width:\s*385px[^}]*flex:\s*0 0 385px[^}]*padding:\s*40px 59px 40px 40px/s);
  assert.match(css, /\.home-center\s*\{[^}]*padding:\s*32px 24px 24px 40px/s);
  assert.match(css, /\.home-ai-hero\s*\{[^}]*min-height:\s*156px[^}]*margin-top:\s*20px/s);
  assert.match(css, /\.home-metrics-section\s*\{[^}]*margin-top:\s*20px/s);
  assert.match(css, /\.home-section-title\s*\{[^}]*margin-bottom:\s*16px/s);
  assert.match(css, /\.home-metrics-grid\s*\{[^}]*max-width:\s*480px[^}]*gap:\s*20px[^}]*margin:\s*0 auto/s);
  assert.match(css, /\.home-metric-card\s*\{[^}]*aspect-ratio:\s*1[^}]*min-height:\s*0[^}]*padding:\s*20px/s);
  assert.match(css, /@media \(max-width:\s*640px\)[\s\S]*?\.home-metric-card\s*\{[^}]*aspect-ratio:\s*auto[^}]*min-height:\s*240px/s);
  assert.match(css, /\.home-reminder-list article\s*\{[^}]*min-height:\s*84px/s);
  assert.match(css, /\.home-report-card\s*\{[^}]*min-height:\s*158px/s);
});
