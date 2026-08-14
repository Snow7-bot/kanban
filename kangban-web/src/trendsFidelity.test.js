import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

test('health trends removes its top navigation and keeps dashboard landmarks', async () => {
  const source = await readFile(new URL('./pages/HealthTrendsPage.jsx', import.meta.url), 'utf8');

  assert.doesNotMatch(source, /TrendsTopbar|trends-topbar|menuOpen|setMenuOpen/);
  for (const landmark of ['trends-left-column', 'trends-right-column', 'trends-footer']) {
    assert.match(source, new RegExp(landmark));
  }
  // Dynamic readings from API, not hardcoded demo values
  assert.match(source, /latest|stats\.latest/);
  assert.match(source, /getUserDisplayName\(user\)/);
  // Compact stat layout classes are still present
  assert.match(source, /StatCard/);
});

test('health trends compacts stat readings when the shared sidebar reduces desktop space', async () => {
  const css = await readFile(new URL('./styles/trends.css', import.meta.url), 'utf8');

  assert.match(css, /@media \(max-width:\s*1280px\)[\s\S]*?\.trends-stat-value\s*\{[^}]*gap:\s*4px/s);
  assert.match(css, /@media \(max-width:\s*1280px\)[\s\S]*?\.trends-stat-value strong\s*\{[^}]*font:\s*700 28px\/36px/s);
  assert.match(css, /@media \(max-width:\s*1280px\)[\s\S]*?\.trends-stat-value span\s*\{[^}]*font-size:\s*14px/s);
});

test('health trends keeps same-day records keyed by record id', async () => {
  const source = await readFile(new URL('./pages/HealthTrendsPage.jsx', import.meta.url), 'utf8');

  assert.match(source, /key=\{record\.id \?\? `\$\{record\.recordedDate\}-\$\{index\}`\}/);
});
