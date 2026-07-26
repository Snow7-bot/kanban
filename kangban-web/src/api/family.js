/**
 * 家庭成员相关 API
 */
import { get, post, put, del, upload } from './request.js';

/** 获取家庭成员列表 */
export function getFamilyMembers() {
  return get('/family');
}

/** 获取成员详情 */
export function getFamilyMember(id) {
  return get(`/family/${id}`);
}

/** 新增家庭成员 */
export function addFamilyMember(data) {
  return post('/family', data);
}

/** 编辑家庭成员 */
export function updateFamilyMember(id, data) {
  return put(`/family/${id}`, data);
}

/** 上传成员头像 */
export function uploadFamilyAvatar(id, file) {
  const fd = new FormData();
  fd.append('file', file);
  return upload(`/family/${id}/avatar`, fd);
}

/** 删除家庭成员 */
export function deleteFamilyMember(id) {
  return del(`/family/${id}`);
}
