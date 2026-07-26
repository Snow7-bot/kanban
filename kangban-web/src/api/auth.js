/**
 * 认证相关 API
 * 登录、注册、验证码、退出、获取当前用户
 */
import { post, get, del } from './request.js';

/** 发送验证码 */
export function sendVerifyCode(phone) {
  return post('/auth/code', { phone });
}

/** 注册 */
export function register(data) {
  return post('/auth/register', data);
}

/** 登录 */
export function login(account, password) {
  return post('/auth/login', { account, password });
}

/** 退出登录 */
export function logout() {
  return del('/auth/logout');
}

/** 获取当前用户信息 */
export function getCurrentUser() {
  return get('/auth/me');
}

/** 刷新 token */
export function refreshToken(refreshToken) {
  return post('/auth/refresh', { refreshToken });
}

/** 忘记密码 - 发送重置验证码 */
export function forgotPassword(phone) {
  return post('/auth/forgot', { phone });
}

/** 重置密码 */
export function resetPassword(data) {
  return post('/auth/reset', data);
}
