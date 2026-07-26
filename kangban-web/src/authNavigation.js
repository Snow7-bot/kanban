const PENDING_ROUTE_KEY = 'kangban_pending_route';

export const PUBLIC_PAGE_IDS = new Set([
  'home',
  'login',
  'register',
  'password-reset',
]);

export function isProtectedPage(pageId) {
  return !PUBLIC_PAGE_IDS.has(pageId);
}

export function rememberPendingRoute(pageId, params = {}) {
  try {
    sessionStorage.setItem(PENDING_ROUTE_KEY, JSON.stringify({ pageId, params }));
  } catch { /* ignore unavailable storage */ }
}

export function consumePendingRoute() {
  try {
    const raw = sessionStorage.getItem(PENDING_ROUTE_KEY);
    sessionStorage.removeItem(PENDING_ROUTE_KEY);
    if (!raw) return null;
    const route = JSON.parse(raw);
    return route?.pageId ? route : null;
  } catch {
    return null;
  }
}

export function clearPendingRoute() {
  try {
    sessionStorage.removeItem(PENDING_ROUTE_KEY);
  } catch { /* ignore unavailable storage */ }
}
