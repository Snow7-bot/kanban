import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const page = (name) => readFile(new URL(`./pages/${name}.jsx`, import.meta.url), 'utf8');

test('the health record page retains the Stitch member, metric, and save controls', async () => {
  const source = await page('HealthRecordPage');
  assert.match(source, /记录健康指标/);
  assert.match(source, /添加成员/);
  assert.match(source, /保存记录/);
});

test('the family add page retains the Stitch form and submit controls', async () => {
  const source = await page('FamilyAddPage');
  assert.match(source, /添加家庭成员/);
  assert.match(source, /保存成员/);
  assert.match(source, /健康备注/);
});

test('the authentication pages retain their selected Stitch form actions', async () => {
  const [login, register] = await Promise.all([page('LoginPage'), page('RegisterPage')]);
  assert.match(login, /欢迎登录/);
  assert.match(login, /立即注册/);
  assert.match(register, /创建康伴账号/);
  assert.match(register, /获取验证码/);
  assert.match(register, /用户服务协议/);
});
