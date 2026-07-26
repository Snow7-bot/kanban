import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

const source = (file) => readFile(new URL(file, import.meta.url), 'utf8');

test('consultation chat follows new messages smoothly without stealing manual scroll', async () => {
  const [page, styles] = await Promise.all([
    source('./pages/ConsultationPage.jsx'),
    source('./styles/global.css'),
  ]);

  assert.match(page, /ref=\{chatMessagesRef\}/);
  assert.match(page, /shouldFollowMessagesRef/);
  assert.match(page, /distanceFromBottom < 72/);
  assert.match(page, /scrollChatToBottom\('smooth'\)/);
  assert.match(styles, /\.chat-messages \{[^}]*min-height: 0;[^}]*overflow-y: auto;/);
  assert.match(styles, /overscroll-behavior: contain/);
  assert.match(styles, /scroll-behavior: smooth/);
});
