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

/** 获取账号型家庭共享、待处理邀请与授权状态 */
export function getFamilySharing() {
  return get('/family/sharing');
}

/** 邀请已注册账号加入家庭并请求指定数据权限 */
export function inviteFamilyAccount(data) {
  return post('/family/sharing/invitations', data);
}

export function acceptFamilyInvitation(id) {
  return post(`/family/sharing/invitations/${id}/accept`, {});
}

export function rejectFamilyInvitation(id) {
  return post(`/family/sharing/invitations/${id}/reject`, {});
}

/** 当前用户调整“对方可查看我的数据”权限 */
export function updateFamilyPermission(granteeUserId, data) {
  return put(`/family/sharing/permissions/${granteeUserId}`, data);
}

/** 当前用户立即撤销对方查看本人数据的权限 */
export function revokeFamilyPermission(granteeUserId) {
  return del(`/family/sharing/permissions/${granteeUserId}`);
}

/** 合并托管档案与已授权账号，供健康和问诊患者切换器使用。 */
export async function getPatientTargets() {
  const [managedMembers, sharing] = await Promise.all([
    getFamilyMembers(),
    getFamilySharing(),
  ]);
  const managed = (managedMembers || []).map((member) => ({
    ...member,
    key: `managed:${member.id}`,
    kind: 'managed',
    memberId: member.id,
    subjectUserId: null,
  }));
  const accounts = (sharing?.sharedSubjects || []).map((account) => ({
    ...account,
    id: `account:${account.userId}`,
    key: `account:${account.userId}`,
    kind: 'account',
    memberId: null,
    subjectUserId: account.userId,
  }));
  return [...managed, ...accounts];
}
