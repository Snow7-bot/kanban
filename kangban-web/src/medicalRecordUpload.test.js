import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

test('medical-record uploads surface backend business errors', async () => {
  const source = await readFile(new URL('./pages/MedicalRecordsPage.jsx', import.meta.url), 'utf8');
  assert.match(source, /data\?\.message \|\| data\?\.msg \|\| msg/);
  assert.match(source, /data\?\.code !== undefined && data\.code !== 0/);
  assert.match(source, /safeParseJson\(selectedRecord\?\.diagnosisData\)/);
  assert.match(source, /selectedRecord\.confidence/);
});
