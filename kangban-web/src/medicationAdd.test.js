import assert from 'node:assert/strict';
import test from 'node:test';
import { readFile } from 'node:fs/promises';
import { normalizePage } from './navigation.js';

test('add medication route retains the Stitch dosage and schedule controls', async () => {
  assert.equal(normalizePage('medication-add'), 'medication-add');
  const [page, medications] = await Promise.all([
    readFile(new URL('./pages/MedicationAddPage.jsx', import.meta.url), 'utf8'),
    readFile(new URL('./pages/MedicationsPage.jsx', import.meta.url), 'utf8'),
  ]);
  for (const label of ['添加新药品', '儿童用药说明', '用药频率', '提醒时间', '保存药品']) assert.match(page, new RegExp(label));
  assert.doesNotMatch(page, /可能与当前服用的降压药有轻微相互作用/);
  assert.match(medications, /medication-add/);
});
