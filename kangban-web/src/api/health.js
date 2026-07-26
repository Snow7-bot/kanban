/**
 * 健康指标相关 API
 */
import { get, post, put, del } from './request.js';

/** 获取健康指标趋势 */
export function getHealthTrends(params) {
  return get('/health/trends', { params });
}

/** 录入健康指标 */
export function addHealthRecord(data) {
  return post('/health/records', data);
}

/** 编辑健康记录 */
export function updateHealthRecord(id, data) {
  return put(`/health/records/${id}`, data);
}

/** 删除健康记录 */
export function deleteHealthRecord(id) {
  return del(`/health/records/${id}`);
}

/** 获取健康报告 */
export function getHealthReport(params) {
  return get('/health/report', { params });
}

/** 获取健康指标列表 */
export function getHealthMetrics() {
  return get('/health/metrics');
}
