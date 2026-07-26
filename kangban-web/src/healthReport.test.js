import assert from 'node:assert/strict';
import test from 'node:test';
import { readFile } from 'node:fs/promises';
import { normalizePage } from './navigation.js';

test('health report route and source preserve the Stitch weekly summary landmarks', async () => {
  assert.equal(normalizePage('health-report'), 'health-report');
  const [page, home] = await Promise.all([
    readFile(new URL('./pages/HealthReportPage.jsx', import.meta.url), 'utf8'),
    readFile(new URL('./pages/HomePage.jsx', import.meta.url), 'utf8'),
  ]);
  for (const landmark of ['本.*健康总结', 'AI 健康洞察', '心率趋势', '睡眠质量', '运动步数', '下次体检提醒']) assert.match(page, new RegExp(landmark));
  assert.match(home, /本周健康总结/);
  assert.match(home, /health-report/);
});
