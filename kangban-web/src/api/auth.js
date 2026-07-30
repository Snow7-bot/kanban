/**
 * 认证相关 API
 * 登录、注册、人机验证、退出、获取当前用户
 */
import { post, get, del } from './request.js';

/** 获取本地图片人机验证 */
export function getCaptcha() {
  return get('/auth/captcha');
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
