import assert from 'node:assert/strict';
import test from 'node:test';
import { readFile } from 'node:fs/promises';

test('home and report read health values from APIs instead of prototype defaults', async () => {
  const [home, report] = await Promise.all([
    readFile(new URL('./pages/HomePage.jsx', import.meta.url), 'utf8'),
    readFile(new URL('./pages/HealthReportPage.jsx', import.meta.url), 'utf8'),
  ]);

  assert.match(home, /healthApi\.getHealthTrends/);
  assert.match(home, /healthApi\.getHealthReport/);
  assert.match(home, /medicationApi\.getMedications/);
  assert.match(report, /healthApi\.getHealthTrends/);
  assert.match(report, /heartRate\?\.average/);
  assert.match(report, /steps\?\.average/);
  assert.doesNotMatch(report, /heartRate\?\.avg/);
});

test('consultation health overview reads recent records instead of prototype readings', async () => {
  const consultation = await readFile(new URL('./pages/ConsultationPage.jsx', import.meta.url), 'utf8');
  assert.match(consultation, /healthApi\.getHealthTrends/);
  assert.match(consultation, /latestHeart/);
  assert.match(consultation, /latestPressure/);
  assert.doesNotMatch(consultation, /72 次\/分/);
  assert.doesNotMatch(consultation, /118\/76/);
});

test('health trend chart does not render prototype readings when records are empty', async () => {
  const chart = await readFile(new URL('./components/HealthChart.jsx', import.meta.url), 'utf8');
  assert.match(chart, /暂无趋势数据/);
  assert.doesNotMatch(chart, /\[120, 118, 122, 116, 121, 119, 123, 117, 120\]/);
});

test('home profile does not show prototype personal data for an incomplete account', async () => {
  const home = await readFile(new URL('./pages/HomePage.jsx', import.meta.url), 'utf8');
  assert.match(home, /getUserDisplayName\(user, '未设置姓名'\)/);
  assert.match(home, /patientAge[\s\S]*?'--'/);
  assert.match(home, /登录后查看/);
  assert.doesNotMatch(home, /'李明'/);
  assert.doesNotMatch(home, /'1986\/07\/20'/);
});

test('profile normalizes nullable backend fields before passing them to controlled inputs', async () => {
  const [profile, authContext] = await Promise.all([
    readFile(new URL('./pages/ProfilePage.jsx', import.meta.url), 'utf8'),
    readFile(new URL('./context/AuthContext.jsx', import.meta.url), 'utf8'),
  ]);
  assert.match(profile, /function normalizeProfile/);
  assert.match(profile, /data\.blood \?\? data\.bloodType/);
  assert.match(profile, /const nextProfile = normalizeProfile\(data\)/);
  assert.match(profile, /setProfile\(nextProfile\)/);
  assert.match(profile, /updateUser\(profilePatch\)/);
  assert.match(profile, /updateUser\(\{ avatarUrl: result\.url \}\)/);
  assert.match(profile, /身高（cm）/);
  assert.match(profile, /体重（kg）/);
  assert.match(authContext, /const updateUser = useCallback/);
  assert.match(authContext, /function withDerivedUserFields/);
  assert.match(authContext, /localStorage\.setItem\(AUTH_USER_KEY, JSON\.stringify\(nextUser\)\)/);
  assert.doesNotMatch(profile, /HIPAA|已验证患者档案|ART-99420/);
});
