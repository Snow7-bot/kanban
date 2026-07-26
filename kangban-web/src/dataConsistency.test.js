import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const source = (file) => readFile(new URL(file, import.meta.url), 'utf8');

test('本人健康数据使用空 memberId，家庭成员使用稳定 ID', async () => {
  const [recordPage, homePage, reportPage] = await Promise.all([
    source('./pages/HealthRecordPage.jsx'),
    source('./pages/HomePage.jsx'),
    source('./pages/HealthReportPage.jsx'),
  ]);
  assert.match(recordPage, /memberId,\n\s+metric:/);
  assert.match(recordPage, /familyApi\.getFamilyMembers/);
  assert.doesNotMatch(homePage, /member:\s*'自己'/);
  assert.doesNotMatch(reportPage, /setMember\('母亲'\)/);
});

test('页面使用后端约定的资料和用药状态字段', async () => {
  const [profilePage, medicationsPage, medicationsApi] = await Promise.all([
    source('./pages/ProfilePage.jsx'),
    source('./pages/MedicationsPage.jsx'),
    source('./api/medications.js'),
  ]);
  assert.match(profilePage, /bloodType:\s*profile\.blood/);
  assert.match(profilePage, /setProfile\(persistedProfile\)/);
  assert.match(medicationsPage, /item\.todayStatus === 'completed'/);
  assert.match(medicationsPage, /firstReminderTime\(item\.times\)/);
  assert.match(medicationsApi, /return page\?\.list \|\| \[\]/);
});

test('异步刷新复用原始请求参数而不是上一次响应数据', async () => {
  const hook = await source('./hooks/useAsync.js');
  assert.match(hook, /lastArgsRef\.current = args/);
  assert.match(hook, /execute\(\.\.\.lastArgsRef\.current\)/);
  assert.doesNotMatch(hook, /execute\(data\)/);
});

test('用户头像统一使用后端 avatarUrl 和同一个默认头像', async () => {
  const [context, topBar, sidebar, home, trends, profile] = await Promise.all([
    source('./context/AuthContext.jsx'),
    source('./components/TopBar.jsx'),
    source('./components/Sidebar.jsx'),
    source('./pages/HomePage.jsx'),
    source('./pages/HealthTrendsPage.jsx'),
    source('./pages/ProfilePage.jsx'),
  ]);
  const surfaces = [topBar, sidebar, home, trends, profile];
  surfaces.forEach((page) => {
    assert.match(page, /DEFAULT_AVATAR_URL/);
    assert.doesNotMatch(page, /user\?\.avatar\b/);
  });
  assert.match(profile, /className="profile-avatar-change" aria-label="更换头像"/);
  assert.match(profile, /<span>更换头像<\/span>/);
  assert.match(context, /delete normalized\.avatar/);
  assert.match(context, /window\.addEventListener\('storage'/);
});

test('家庭成员头像可上传且 AI 不使用本人头像代替家属', async () => {
  const [familyApi, addPage, familyPage, consultation] = await Promise.all([
    source('./api/family.js'),
    source('./pages/FamilyAddPage.jsx'),
    source('./pages/FamilyPage.jsx'),
    source('./pages/ConsultationPage.jsx'),
  ]);
  assert.match(familyApi, /uploadFamilyAvatar/);
  assert.match(familyApi, /\/family\/\$\{id\}\/avatar/);
  assert.match(addPage, /familyApi\.uploadFamilyAvatar/);
  assert.match(familyPage, /family-avatar-fallback/);
  assert.match(consultation, /selectedMember \? selectedMember\.avatarUrl : \(user\?\.avatarUrl \|\| null\)/);
});
