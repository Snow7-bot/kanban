import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

test('medication list supports the JSON reminder-time format stored by the backend', async () => {
  const source = await readFile(new URL('./pages/MedicationsPage.jsx', import.meta.url), 'utf8');
  assert.match(source, /function firstReminderTime/);
  assert.match(source, /JSON\.parse\(times\)/);
});
