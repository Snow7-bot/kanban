/**
 * 用药管理相关 API
 */
import { get, post, put, del } from './request.js';

/** 获取用药列表 */
export async function getMedications(params) {
  const page = await get('/medications', { params });
  return page?.list || [];
}

/** 获取药品详情 */
export function getMedication(id) {
  return get(`/medications/${id}`);
}

/** 新增药品 */
export function addMedication(data) {
  return post('/medications', data);
}

/** 编辑药品 */
export function updateMedication(id, data) {
  return put(`/medications/${id}`, data);
}

/** 删除药品 */
export function deleteMedication(id) {
  return del(`/medications/${id}`);
}

/** 确认服药 */
export function confirmDose(id) {
  return post(`/medications/${id}/confirm`);
}

/** 检查药物相互作用 */
export function checkInteraction(drugIds) {
  return post('/medications/interaction', { drugIds });
}

/** 获取用药历史 */
export function getMedicationHistory(medicationId) {
  return get('/medications/history', { params: { medicationId } });
}

/** 搜索药品 */
export function searchDrugs(keyword) {
  return get('/medications/search', { params: { keyword } });
}
