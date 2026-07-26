import test from 'node:test';
import assert from 'node:assert/strict';
import { shellModeForPage } from './shellMode.js';

test('home and trends use the shared app shell', () => {
  assert.equal(shellModeForPage('home'), 'app');
  assert.equal(shellModeForPage('trends'), 'app');
});

test('consultation, records, and medications keep the shared app shell', () => {
  assert.equal(shellModeForPage('consultation'), 'app');
  assert.equal(shellModeForPage('records'), 'app');
  assert.equal(shellModeForPage('medications'), 'app');
});
