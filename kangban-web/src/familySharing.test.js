import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const source = (file) => readFile(new URL(file, import.meta.url), 'utf8');

test('家庭账号共享使用邀请确认、精细权限和即时撤销接口', async () => {
  const [panel, api] = await Promise.all([
    source('./components/FamilySharingPanel.jsx'),
    source('./api/family.js'),
  ]);
  assert.match(panel, /接受邀请即表示同意/);
  assert.match(panel, /canViewHealth/);
  assert.match(panel, /canAddHealth/);
  assert.match(panel, /canUseAi/);
  assert.match(panel, /撤销全部/);
  assert.match(api, /family\/sharing\/invitations\/\$\{id\}\/accept/);
  assert.match(api, /family\/sharing\/permissions\/\$\{granteeUserId\}/);
});

test('健康与 AI 患者选择器只显示对应权限的共享账号', async () => {
  const [record, report, trends, consultation] = await Promise.all([
    source('./pages/HealthRecordPage.jsx'),
    source('./pages/HealthReportPage.jsx'),
    source('./pages/HealthTrendsPage.jsx'),
    source('./pages/ConsultationPage.jsx'),
  ]);
  assert.match(record, /permissions\?\.canAddHealth/);
  assert.match(report, /permissions\?\.canViewReports/);
  assert.match(trends, /permissions\?\.canViewHealth/);
  assert.match(consultation, /permissions\?\.canUseAi/);
});
