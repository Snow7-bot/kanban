/**
 * 导航工具：支持参数化 Hash 路由
 * 格式：#/pageId?param1=value1&param2=value2
 */

export const PAGE_IDS = [
  'home', 'profile', 'family', 'family-add', 'health-record',
  'health-report', 'trends', 'records', 'record-detail',
  'consultation', 'medications', 'medication-add',
  'settings', 'login', 'register', 'password-reset',
  'shared-record',
];

export function normalizePage(value) {
  return PAGE_IDS.includes(value) ? value : 'home';
}

/**
 * 从 hash 解析 pageId 和 params
 * @param {string} hash - window.location.hash
 * @returns {{ pageId: string, params: object }}
 */
export function pageFromHash(hash = '') {
  const clean = hash.replace(/^#\/?/, '');
  const [pathPart, queryPart] = clean.split('?');
  let pageId = normalizePage(pathPart);
  const params = {};

  // Handle path-based routes like shared-record/TOKEN
  if (!PAGE_IDS.includes(pageId)) {
    const pathParts = pathPart.split('/');
    const candidate = pathParts[0];
    if (PAGE_IDS.includes(candidate)) {
      pageId = candidate;
      // Store remaining path segments as a combined token
      if (pathParts.length > 1) {
        params.token = pathParts.slice(1).join('/');
      }
    }
  }

  if (queryPart) {
    queryPart.split('&').forEach(pair => {
      const [k, v] = pair.split('=');
      if (k) params[decodeURIComponent(k)] = v ? decodeURIComponent(v) : '';
    });
  }
  return { pageId, params };
}

/**
 * 生成带参数的 hash
 * @param {string} pageId
 * @param {object} [params]
 * @returns {string}
 */
export function hashForPage(pageId, params = {}) {
  const base = `#${normalizePage(pageId)}`;
  const entries = Object.entries(params).filter(([, v]) => v !== undefined && v !== null && v !== '');
  if (entries.length === 0) return base;
  const query = entries.map(([k, v]) => `${encodeURIComponent(k)}=${encodeURIComponent(String(v))}`).join('&');
  return `${base}?${query}`;
}
