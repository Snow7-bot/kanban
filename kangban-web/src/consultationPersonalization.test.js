import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const source = (file) => readFile(new URL(file, import.meta.url), 'utf8');

test('AI consultation switches authorized family members into isolated sessions', async () => {
  const [page, api, contracts] = await Promise.all([
    source('./pages/ConsultationPage.jsx'),
    source('./api/consultation.js'),
    source('./api/contracts.js'),
  ]);

  assert.match(page, /familyApi\.getPatientTargets\(\)/);
  assert.match(page, /切换至\$\{nextName\}的独立问诊/);
  assert.match(page, /pendingSummaryMemberRef/);
  assert.match(page, /appendPatientSummary\(sid\)/);
  assert.match(page, /getChatSessions\(\{/);
  assert.match(page, /contextVersion !== 'family-agent-v2'/);
  assert.match(page, /hasPersonalizedContext\(session, selectedMember\)/);
  assert.match(page, /Number\(session\.subjectUserId\) === Number\(selectedTarget\.subjectUserId\)/);
  assert.match(page, /subjectUserId: selectedMember\?\.subjectUserId/);
  assert.match(page, /当前分析仅使用\{patientDisplay\.name\}的授权健康数据/);
  assert.match(api, /params: \{ memberId, subjectUserId \}/);
  assert.match(api, /sessions\/\$\{sessionId\}\/summary/);
  assert.match(contracts, /subjectUserId: member\?\.subjectUserId \?\? null/);
});
