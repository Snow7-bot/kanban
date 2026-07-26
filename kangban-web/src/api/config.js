/**
 * API 配置
 * 基础配置从环境变量读取，不可在代码中写入真实密钥
 */

const BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080';
const TIMEOUT = parseInt(import.meta.env.VITE_API_TIMEOUT || '15000', 10);

export const API_CONFIG = {
  BASE_URL,
  TIMEOUT,
  UPLOAD_MAX_SIZE: parseInt(import.meta.env.VITE_UPLOAD_MAX_SIZE || '10485760', 10),
};

export const AUTH_TOKEN_KEY = 'kangban_auth_token';
export const AUTH_REFRESH_TOKEN_KEY = 'kangban_auth_refresh_token';
export const AUTH_USER_KEY = 'kangban_auth_user';
