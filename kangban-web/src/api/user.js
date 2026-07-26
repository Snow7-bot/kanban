/**
 * 用户相关 API
 * 个人资料查询、编辑、头像上传
 */
import { get, put, upload } from './request.js';

/** 获取个人资料 */
export function getProfile() {
  return get('/user/profile');
}

/** 更新个人资料 */
export function updateProfile(data) {
  return put('/user/profile', data);
}

/** 上传头像 */
export function uploadAvatar(file) {
  const fd = new FormData();
  fd.append('file', file);
  return upload('/user/avatar', fd);
}
