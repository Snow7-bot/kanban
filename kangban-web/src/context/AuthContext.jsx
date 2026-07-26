/**
 * 认证上下文
 * 管理登录态、token 存储、全局 401 监听
 */
import { createContext, useContext, useState, useEffect, useCallback, useRef } from 'react';
import { AUTH_REFRESH_TOKEN_KEY, AUTH_TOKEN_KEY, AUTH_USER_KEY } from '../api/config.js';
import { setLoadingCallback } from '../api/request.js';
import * as authApi from '../api/auth.js';

const AuthContext = createContext(null);

function clearAuthStorage() {
  try {
    localStorage.removeItem(AUTH_TOKEN_KEY);
    localStorage.removeItem(AUTH_REFRESH_TOKEN_KEY);
    localStorage.removeItem(AUTH_USER_KEY);
  } catch { /* ignore */ }
}

function hasStoredToken() {
  try {
    return !!localStorage.getItem(AUTH_TOKEN_KEY);
  } catch {
    return false;
  }
}

function normalizeUserFields(data = {}) {
  const normalized = { ...(data || {}) };
  if (Object.hasOwn(normalized, 'avatar') || Object.hasOwn(normalized, 'avatarUrl')) {
    normalized.avatarUrl = normalized.avatarUrl ?? normalized.avatar ?? '';
  }
  delete normalized.avatar;
  return normalized;
}

function withDerivedUserFields(data = {}) {
  const normalized = normalizeUserFields(data);
  if (!normalized?.birthday) return normalized;
  const birthday = new Date(`${normalized.birthday}T00:00:00`);
  if (Number.isNaN(birthday.getTime())) return normalized;
  const today = new Date();
  let age = today.getFullYear() - birthday.getFullYear();
  const beforeBirthday = today.getMonth() < birthday.getMonth()
    || (today.getMonth() === birthday.getMonth() && today.getDate() < birthday.getDate());
  if (beforeBirthday) age -= 1;
  return { ...normalized, age: Math.max(0, age) };
}

export function AuthProvider({ children }) {
  const [user, setUser] = useState(() => {
    try {
      if (!localStorage.getItem(AUTH_TOKEN_KEY)) return null;
      const raw = localStorage.getItem(AUTH_USER_KEY);
      return raw ? withDerivedUserFields(JSON.parse(raw)) : null;
    } catch {
      return null;
    }
  });
  const [authReady, setAuthReady] = useState(() => !hasStoredToken());
  const [loading, setLoading] = useState(false);
  const [globalLoading, setGlobalLoading] = useState(false);
  const unauthorizedHandledAtRef = useRef(0);

  // 注入全局 loading 回调
  useEffect(() => {
    setLoadingCallback(setGlobalLoading);
  }, []);

  useEffect(() => {
    const handleStorage = (event) => {
      if (event.key !== AUTH_USER_KEY) return;
      try {
        setUser(event.newValue ? withDerivedUserFields(JSON.parse(event.newValue)) : null);
      } catch {
        setUser(null);
      }
    };
    window.addEventListener('storage', handleStorage);
    return () => window.removeEventListener('storage', handleStorage);
  }, []);

  // 401 监听：清除登录态并返回访客首页
  useEffect(() => {
    const handler = () => {
      const now = Date.now();
      if (now - unauthorizedHandledAtRef.current < 1500) return;
      unauthorizedHandledAtRef.current = now;
      setUser(null);
      setAuthReady(true);
      clearAuthStorage();
      window.location.hash = '#home';
      window.dispatchEvent(new CustomEvent('app:error', {
        detail: '登录状态已失效，请重新登录',
      }));
    };
    window.addEventListener('auth:unauthorized', handler);
    return () => window.removeEventListener('auth:unauthorized', handler);
  }, []);

  const isAuthenticated = authReady && !!user;

  const login = useCallback(async (account, password) => {
    setLoading(true);
    try {
      const result = await authApi.login(account, password);
      const { token, refreshToken, user: userData } = result;
      const nextUser = withDerivedUserFields(userData || { account });
      if (token) {
        try {
          localStorage.setItem(AUTH_TOKEN_KEY, token);
          if (refreshToken) localStorage.setItem(AUTH_REFRESH_TOKEN_KEY, refreshToken);
          localStorage.setItem(AUTH_USER_KEY, JSON.stringify(nextUser));
        } catch { /* ignore */ }
      }
      setUser(nextUser);
      setAuthReady(true);
      return result;
    } finally {
      setLoading(false);
    }
  }, []);

  const register = useCallback(async (data) => {
    setLoading(true);
    try {
      const result = await authApi.register(data);
      return result;
    } finally {
      setLoading(false);
    }
  }, []);

  const logout = useCallback(async () => {
    try {
      await authApi.logout();
    } catch { /* ignore offline logout */ }
    setUser(null);
    setAuthReady(true);
    clearAuthStorage();
    window.location.hash = '#home';
  }, []);

  const refreshUser = useCallback(async () => {
    try {
      const userData = await authApi.getCurrentUser();
      const nextUser = withDerivedUserFields(userData);
      setUser(nextUser);
      setAuthReady(true);
      try {
        localStorage.setItem(AUTH_USER_KEY, JSON.stringify(nextUser));
      } catch { /* ignore */ }
    } catch {
      setUser(null);
      setAuthReady(true);
      clearAuthStorage();
      window.location.hash = '#home';
    }
  }, []);

  const updateUser = useCallback((patch) => {
    setUser((current) => {
      const nextUser = withDerivedUserFields({
        ...normalizeUserFields(current),
        ...normalizeUserFields(patch),
      });
      try {
        localStorage.setItem(AUTH_USER_KEY, JSON.stringify(nextUser));
      } catch { /* ignore */ }
      return nextUser;
    });
  }, []);

  useEffect(() => {
    try {
      if (!localStorage.getItem(AUTH_TOKEN_KEY)) {
        setUser(null);
        setAuthReady(true);
        return;
      }
    } catch {
      return;
    }
    refreshUser();
  }, [refreshUser]);

  const value = {
    user,
    loading,
    globalLoading,
    authReady,
    isAuthenticated,
    login,
    register,
    logout,
    refreshUser,
    updateUser,
  };

  return (
    <AuthContext.Provider value={value}>
      {children}
      {globalLoading && (
        <div className="global-loading-overlay" aria-label="加载中">
          <div className="global-loading-spinner" />
        </div>
      )}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) {
    throw new Error('useAuth 必须在 AuthProvider 内使用');
  }
  return ctx;
}
