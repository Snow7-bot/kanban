import test from 'node:test';
import assert from 'node:assert/strict';
import { hashForPage, normalizePage, pageFromHash } from './navigation.js';
import { NAV_ITEMS } from './data.js';

test('normalizes supported page ids and falls back to home', () => {
  assert.equal(normalizePage('trends'), 'trends');
  assert.equal(normalizePage('records'), 'records');
  assert.equal(normalizePage('family'), 'family');
  assert.equal(normalizePage('profile'), 'profile');
  assert.equal(normalizePage('settings'), 'settings');
  assert.equal(normalizePage('health-record'), 'health-record');
  assert.equal(normalizePage('health-report'), 'health-report');
  assert.equal(normalizePage('record-detail'), 'record-detail');
  assert.equal(normalizePage('medication-add'), 'medication-add');
  assert.equal(normalizePage('family-add'), 'family-add');
  assert.equal(normalizePage('login'), 'login');
  assert.equal(normalizePage('register'), 'register');
  assert.equal(normalizePage('password-reset'), 'password-reset');
  assert.equal(normalizePage('unknown'), 'home');
});

test('reads a page id from a hash without throwing on malformed input', () => {
  assert.equal(pageFromHash('#consultation').pageId, 'consultation');
  assert.equal(pageFromHash('#/medications').pageId, 'medications');
  assert.equal(pageFromHash('#family').pageId, 'family');
  assert.equal(pageFromHash('#/profile').pageId, 'profile');
  assert.equal(pageFromHash('#settings').pageId, 'settings');
  assert.equal(pageFromHash('#health-record').pageId, 'health-record');
  assert.equal(pageFromHash('#health-report').pageId, 'health-report');
  assert.equal(pageFromHash('#record-detail').pageId, 'record-detail');
  assert.equal(pageFromHash('#medication-add').pageId, 'medication-add');
  assert.equal(pageFromHash('#family-add').pageId, 'family-add');
  assert.equal(pageFromHash('#login').pageId, 'login');
  assert.equal(pageFromHash('#register').pageId, 'register');
  assert.equal(pageFromHash('#password-reset').pageId, 'password-reset');
  assert.equal(pageFromHash('').pageId, 'home');
  assert.equal(pageFromHash('#not-a-page').pageId, 'home');
});

test('reads url params from a parameterized hash', () => {
  const result = pageFromHash('#record-detail?id=abc123');
  assert.equal(result.pageId, 'record-detail');
  assert.equal(result.params.id, 'abc123');
});

test('reads multiple url params', () => {
  const result = pageFromHash('#family?name=张三&age=30');
  assert.equal(result.pageId, 'family');
  assert.equal(result.params.name, '张三');
  assert.equal(result.params.age, '30');
});

test('builds a parameterized hash for record details', () => {
  assert.equal(hashForPage('record-detail', { id: 42 }), '#record-detail?id=42');
});

test('returns empty params when no query string', () => {
  const result = pageFromHash('#home');
  assert.equal(result.pageId, 'home');
  assert.deepEqual(result.params, {});
});

test('uses the same expanded navigation set for every page', () => {
  assert.deepEqual(NAV_ITEMS.map(({ id }) => id), [
    'home', 'profile', 'family', 'health-record', 'trends', 'records', 'consultation', 'medications', 'settings',
  ]);
  assert.equal(NAV_ITEMS.find(({ id }) => id === 'home')?.label, '首页');
});
