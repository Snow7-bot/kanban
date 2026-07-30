import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const source = (file) => readFile(new URL(file, import.meta.url), 'utf8');

test('病历 PDF 下载提供超时、处理中状态和错误反馈', async () => {
  const [api, page] = await Promise.all([
    source('./api/medicalRecords.js'),
    source('./pages/RecordDetailPage.jsx'),
  ]);

  assert.match(api, /AbortController/);
  assert.match(api, /60_000/);
  assert.match(api, /PDF 生成超时，请稍后重试/);
  assert.match(page, /pdfLoading/);
  assert.match(page, /生成中/);
  assert.match(page, /disabled=\{Boolean\(pdfLoading\)\}/);
});
