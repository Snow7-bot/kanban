import assert from 'node:assert/strict';
import test from 'node:test';
import { readFile } from 'node:fs/promises';
import { normalizePage } from './navigation.js';

test('record detail keeps the Stitch diagnosis, prescription, advice, and scan preview', async () => {
  assert.equal(normalizePage('record-detail'), 'record-detail');
  const [detail, records] = await Promise.all([
    readFile(new URL('./pages/RecordDetailPage.jsx', import.meta.url), 'utf8'),
    readFile(new URL('./pages/MedicalRecordsPage.jsx', import.meta.url), 'utf8'),
  ]);
  for (const label of ['病历详情', 'AI 结构化诊断', '处方用药', '医嘱与随访', '原件扫描副本']) assert.match(detail, new RegExp(label));
  assert.match(records, /record-detail/);
});
