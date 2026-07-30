import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

test('registration uses a local captcha and no SMS endpoints', async () => {
  const [api, register, reset] = await Promise.all([
    readFile(new URL('./api/auth.js', import.meta.url), 'utf8'),
    readFile(new URL('./pages/RegisterPage.jsx', import.meta.url), 'utf8'),
    readFile(new URL('./pages/PasswordResetPage.jsx', import.meta.url), 'utf8'),
  ]);

  assert.match(api, /get\('\/auth\/captcha'\)/);
  assert.doesNotMatch(api, /\/auth\/code|\/auth\/forgot|\/auth\/reset/);
  assert.match(register, /username: username\.trim\(\)/);
  assert.match(register, /captchaId: captcha\.captchaId/);
  assert.match(register, /captchaAnswer: captchaAnswer\.trim\(\)/);
  assert.match(register, /手机号码（选填）/);
  assert.match(reset, /13602060910/);
});
