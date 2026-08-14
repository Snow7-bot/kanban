import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import test from 'node:test';

test('production API can use same-origin proxy and production Agent enables RAG', async () => {
  const [config, productionEnv, appEnv, nginx] = await Promise.all([
    readFile(new URL('./api/config.js', import.meta.url), 'utf8'),
    readFile(new URL('../.env.production.example', import.meta.url), 'utf8'),
    readFile(new URL('../../deploy/ecs/app.env.example', import.meta.url), 'utf8'),
    readFile(new URL('../../deploy/ecs/nginx.conf', import.meta.url), 'utf8'),
  ]);

  assert.match(config, /configuredBaseUrl\s*\?\?/);
  assert.match(config, /import\.meta\.env\.PROD[\s\S]*localBaseUrl/);
  assert.match(productionEnv, /VITE_API_BASE_URL=\s*(?:#|$)/m);
  assert.match(appEnv, /APP_RAG_ENABLED=true/);
  assert.match(nginx, /proxy_pass http:\/\/127\.0\.0\.1:8081;/);
});
