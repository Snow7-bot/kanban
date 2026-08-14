import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

test('authentication persists refresh tokens and retries one expired request', async () => {
  const [config, context, request] = await Promise.all([
    readFile(new URL('./api/config.js', import.meta.url), 'utf8'),
    readFile(new URL('./context/AuthContext.jsx', import.meta.url), 'utf8'),
    readFile(new URL('./api/request.js', import.meta.url), 'utf8'),
  ]);
  assert.match(config, /AUTH_REFRESH_TOKEN_KEY/);
  assert.match(context, /localStorage\.setItem\(AUTH_REFRESH_TOKEN_KEY, refreshToken\)/);
  assert.match(context, /refreshUser[\s\S]*catch \{[\s\S]*clearAuthStorage\(\)/);
  assert.match(context, /authReady && !!user/);
  assert.match(context, /setAuthReady\(true\)/);
  assert.match(request, /refreshAccessToken/);
  assert.match(request, /_authRetry/);
  assert.match(request, /finally \{[\s\S]*clearTimeout\(timeoutId\)/);
});

test('login links to the password reset page', async () => {
  const source = await readFile(new URL('./pages/LoginPage.jsx', import.meta.url), 'utf8');
  assert.match(source, /onNavigate\('password-reset'\)/);
});

test('login password controls are not nested in the forgot-password label', async () => {
  const source = await readFile(new URL('./pages/LoginPage.jsx', import.meta.url), 'utf8');
  assert.match(source, /<label htmlFor="login-password">密码<\/label>/);
  assert.match(source, /<input id="login-password"/);
  assert.match(source, /event\.preventDefault\(\); event\.stopPropagation\(\)/);
  assert.doesNotMatch(source, /<label><span>密码<button/);
});

test('password visibility controls stay inside every password field', async () => {
  const [login, register, styles] = await Promise.all([
    readFile(new URL('./pages/LoginPage.jsx', import.meta.url), 'utf8'),
    readFile(new URL('./pages/RegisterPage.jsx', import.meta.url), 'utf8'),
    readFile(new URL('./styles/stitch-forms.css', import.meta.url), 'utf8'),
  ]);

  for (const source of [login, register]) {
    assert.match(source, /className="password-visibility-toggle"/);
  }
  assert.match(styles, /\.login-form label > div > svg/);
  assert.match(styles, /\.password-visibility-toggle \{[^}]*position: absolute;[^}]*right: 8px;/);
  assert.match(styles, /\.password-visibility-toggle svg \{ position: static;/);
});

test('password recovery directs users to the configured administrator', async () => {
  const source = await readFile(new URL('./pages/PasswordResetPage.jsx', import.meta.url), 'utf8');
  assert.match(source, /请联系管理员/);
  assert.match(source, /tel:13602060910/);
  assert.doesNotMatch(source, /resetPassword|验证码已发送/);
});
