import test from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';

async function source(path) {
  return readFile(new URL(path, import.meta.url), 'utf8');
}

test('knowledge admin page keeps the admin token in memory and exposes lifecycle actions', async () => {
  const [page, api] = await Promise.all([
    source('./pages/KnowledgeAdminPage.jsx'),
    source('./api/knowledge.js'),
  ]);
  assert.match(page, /X-Knowledge-Admin-Token/);
  assert.match(page, /上传并解析/);
  assert.match(page, /送审/);
  assert.match(page, /重建索引/);
  assert.match(api, /admin\/knowledge\/documents/);
  assert.match(api, /submit-review/);
  assert.match(api, /reindex/);
  assert.doesNotMatch(page, /localStorage\.setItem/);
  assert.doesNotMatch(api, /YOUR_|CHANGE_ME/);
});

test('knowledge admin route is protected and not added to the patient navigation list', async () => {
  const [navigation, data, authNavigation] = await Promise.all([
    source('./navigation.js'),
    source('./data.js'),
    source('./authNavigation.js'),
  ]);
  assert.match(navigation, /knowledge-admin/);
  assert.doesNotMatch(data, /knowledge-admin/);
  assert.doesNotMatch(authNavigation, /knowledge-admin.*PUBLIC_PAGE_IDS/);
});
