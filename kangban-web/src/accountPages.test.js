import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

test('the three Stitch account screens share the prototype footer', async () => {
  const files = ['FamilyPage.jsx', 'ProfilePage.jsx', 'SettingsPage.jsx'];
  for (const file of files) {
    const source = await readFile(new URL(`./pages/${file}`, import.meta.url), 'utf8');
    assert.match(source, /AccountFooter/);
  }
});

test('family member cards do not nest delete buttons inside their selectable button', async () => {
  const source = await readFile(new URL('./pages/FamilyPage.jsx', import.meta.url), 'utf8');
  assert.match(source, /<article\s+key=\{member\.id\}/);
  assert.match(source, /className="family-member-select"/);
  assert.match(source, /className="family-delete-btn"/);
  assert.doesNotMatch(source, /family-member-card[\s\S]*family-delete-btn[\s\S]*<\/button>\s*<\/button>/);
});
