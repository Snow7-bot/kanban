/**
 * 统一请求模块
 * 基于 fetch 封装：自动注入 token、401 拦截、业务错误处理、网络重试、loading 回调
 */

import { API_CONFIG, AUTH_REFRESH_TOKEN_KEY, AUTH_TOKEN_KEY, AUTH_USER_KEY } from './config.js';

// 重试配置
const MAX_RETRIES = 1;
const RETRY_DELAY = 1000;

// 全局 loading 回调（由 AuthProvider 注入）
let _loadingCallback = null;

export function setLoadingCallback(cb) {
  _loadingCallback = cb;
}

function getToken() {
  try {
    return localStorage.getItem(AUTH_TOKEN_KEY);
  } catch {
    return null;
  }
}

function clearAuthStorage() {
  try {
    localStorage.removeItem(AUTH_TOKEN_KEY);
    localStorage.removeItem(AUTH_REFRESH_TOKEN_KEY);
    localStorage.removeItem(AUTH_USER_KEY);
  } catch { /* ignore */ }
}

function buildURL(path, params) {
  // 确保 BASE_URL 和 path 正确拼接
  let base = API_CONFIG.BASE_URL;
  if (path.startsWith('http')) {
    base = '';
  }
  // 确保 base 末尾没有 / 且 path 开头没有 /
  const cleanBase = base.replace(/\/+$/, '');
  const cleanPath = path.startsWith('/') ? path : `/${path}`;
  const urlStr = `${cleanBase}${cleanPath}`;

  // 用 window.location.origin 确保相对路径能正确解析
  const url = urlStr.startsWith('http')
    ? new URL(urlStr)
    : new URL(urlStr, window.location.origin);

  if (params) {
    Object.entries(params).forEach(([key, value]) => {
      if (value !== undefined && value !== null && value !== '') {
        url.searchParams.set(key, String(value));
      }
    });
  }
  return url.toString();
}

function shouldRetry(status) {
  return status === 0 || status === 502 || status === 503 || status === 504;
}

function delay(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function refreshAccessToken() {
  let refreshToken;
  try {
    refreshToken = localStorage.getItem(AUTH_REFRESH_TOKEN_KEY);
  } catch {
    return false;
  }
  if (!refreshToken) return false;

  try {
    const response = await fetch(buildURL('/auth/refresh'), {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken }),
    });
    if (!response.ok) return false;
    const payload = await response.json();
    const token = payload?.code === 0 ? payload?.data?.token : null;
    if (!token) return false;
    localStorage.setItem(AUTH_TOKEN_KEY, token);
    return true;
  } catch {
    return false;
  }
}

/**
 * 构建请求头
 * @param {boolean} skipAuth - 是否跳过 Authorization
 * @param {object} customHeaders - 自定义请求头
 * @returns {object}
 */
function buildHeaders(skipAuth, customHeaders) {
  const headers = { ...(customHeaders || {}) };
  if (!skipAuth) {
    const token = getToken();
    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }
  }
  if (!headers['Content-Type'] && !(headers.body instanceof FormData)) {
    headers['Content-Type'] = 'application/json';
  }
  return headers;
}

/**
 * 核心请求函数
 * @param {string} path - 请求路径
 * @param {object} options
 * @param {string} [options.method='GET']
 * @param {object|FormData} [options.body]
 * @param {object} [options.params] - URL 查询参数
 * @param {object} [options.headers] - 额外请求头（传入后不会再被覆盖）
 * @param {boolean} [options.skipAuth=false] - 跳过 token 注入
 * @param {boolean} [options.skipLoading=false] - 跳过 loading
 * @param {boolean} [options.skipErrorToast=false] - 跳过业务错误提示
 * @param {number} [options.retries=MAX_RETRIES] - 重试次数
 * @returns {Promise<any>} - 成功时返回 data 字段
 */
export async function request(path, options = {}) {
  const {
    method = 'GET',
    body,
    params,
    headers: customHeaders,
    skipAuth = false,
    skipLoading = false,
    skipErrorToast = false,
    retries = MAX_RETRIES,
    _authRetry = false,
  } = options;

  const url = buildURL(path, params);

  // 构建请求头
  const headers = buildHeaders(skipAuth, customHeaders);

  const fetchOptions = { method, headers };

  if (body) {
    fetchOptions.body = body instanceof FormData ? body : JSON.stringify(body);
  }

  // 如果是 FormData，删除 Content-Type 让浏览器自动设置 multipart boundary
  if (body instanceof FormData) {
    delete fetchOptions.headers['Content-Type'];
  }

  if (!skipLoading && _loadingCallback) {
    _loadingCallback(true);
  }

  let lastError = null;

  for (let attempt = 0; attempt <= retries; attempt++) {
    let timeoutId = null;
    try {
      const controller = new AbortController();
      timeoutId = setTimeout(() => controller.abort(), API_CONFIG.TIMEOUT);
      fetchOptions.signal = controller.signal;

      const response = await fetch(url, fetchOptions);

      if (!skipLoading && _loadingCallback) {
        _loadingCallback(false);
      }

      // 401：登录失效
      if (response.status === 401) {
        const canRefresh = !skipAuth && !_authRetry && path !== '/auth/refresh';
        if (canRefresh && await refreshAccessToken()) {
          return request(path, { ...options, _authRetry: true });
        }
        clearAuthStorage();
        window.dispatchEvent(new CustomEvent('auth:unauthorized'));
        throw new ApiError('登录已失效，请重新登录', 401);
      }

      // 尝试解析 JSON
      let data;
      const contentType = response.headers.get('content-type') || '';
      if (contentType.includes('application/json')) {
        data = await response.json();
      } else {
        const text = await response.text();
        data = { code: response.status, message: text, data: null };
      }

      // 业务层错误（后端约定：code !== 0）
      if (data && data.code !== undefined && data.code !== 0) {
        const errMsg = data.message || data.msg || '请求失败';
        if (!skipErrorToast) {
          window.dispatchEvent(new CustomEvent('app:error', { detail: errMsg }));
        }
        throw new ApiError(errMsg, data.code || response.status, data);
      }

      return data?.data !== undefined ? data.data : data;
    } catch (err) {
      if (!skipLoading && _loadingCallback) {
        _loadingCallback(false);
      }

      lastError = err;

      if (err instanceof ApiError) {
        throw err; // 业务错误不重试
      }

      // 网络错误 / 超时 — 可重试
      const isNetworkError = err.name === 'TypeError' || err.name === 'AbortError';
      if (isNetworkError && attempt < retries) {
        await delay(RETRY_DELAY * (attempt + 1));
        continue;
      }

      const msg = err.name === 'AbortError' ? '请求超时，请检查网络' : '网络异常，请检查网络连接';
      if (!skipErrorToast) {
        window.dispatchEvent(new CustomEvent('app:error', { detail: msg }));
      }
      throw new ApiError(msg, 0);
    } finally {
      if (timeoutId !== null) clearTimeout(timeoutId);
    }
  }

  throw lastError || new ApiError('请求失败', 0);
}

/** GET 请求 */
export function get(path, options = {}) {
  return request(path, { ...options, method: 'GET' });
}

/** POST 请求 */
export function post(path, body, options = {}) {
  return request(path, { ...options, method: 'POST', body });
}

/** PUT 请求 */
export function put(path, body, options = {}) {
  return request(path, { ...options, method: 'PUT', body });
}

/** DELETE 请求 */
export function del(path, options = {}) {
  return request(path, { ...options, method: 'DELETE' });
}

/** 上传文件 */
export function upload(path, formData, options = {}) {
  return request(path, { ...options, method: 'POST', body: formData, skipAuth: false });
}

/** API 错误类 */
export class ApiError extends Error {
  constructor(message, code = -1, data = null) {
    super(message);
    this.name = 'ApiError';
    this.code = code;
    this.data = data;
  }
}
