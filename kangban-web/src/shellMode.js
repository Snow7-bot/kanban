export function shellModeForPage(pageId) {
  if (pageId === 'login' || pageId === 'register' || pageId === 'password-reset') return 'auth';
  return 'app';
}
