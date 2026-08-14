import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const source = (file) => readFile(new URL(file, import.meta.url), 'utf8');

test('consultation retry reuses the saved message and closes stale SSE connections', async () => {
  const [page, api] = await Promise.all([
    source('./pages/ConsultationPage.jsx'),
    source('./api/consultation.js'),
  ]);

  assert.match(page, /new URLSearchParams\(\{ messageId: String\(messageId\) \}\)/);
  assert.match(page, /eventSourceRef\.current\?\.close\(\)/);
  assert.match(page, /Authorization: `Bearer \$\{token\}`/);
  assert.match(page, /fetch\(url,/);
  assert.doesNotMatch(page, /token: token \|\| ''/);
  assert.doesNotMatch(page, /new EventSource\(/);
  assert.match(page, /failedMessageIdRef\.current/);
  assert.match(page, /onClick=\{retryLastResponse\}/);
  assert.doesNotMatch(page, /sendMessage\(lastMsg\.content\)/);
  assert.match(page, /sendingRef\.current/);
  assert.match(page, /eventName === 'agent_tool'/);
  assert.match(page, /agentToolTraces: receivedToolTraces/);
  assert.match(page, /streamingToolTraces\.length > 0 && \(thinking \|\| streaming\)/);
  assert.match(page, /AI 不用于诊断、处方或调整剂量/);
  assert.match(page, /拨打 120 或前往急诊/);
  assert.match(api, /\{ content, clientMessageId \}/);
});
