/**
 * 病历相关 API
 */
import { get, post, del, upload } from './request.js';
import { API_CONFIG, AUTH_TOKEN_KEY } from './config.js';

/** 获取病历列表 */
export async function getMedicalRecords(params) {
  const page = await get('/medical-records', { params });
  return page?.list || [];
}

/** 获取病历详情 */
export function getMedicalRecord(id) {
  return get(`/medical-records/${id}`);
}

/** 上传病历 */
export function uploadMedicalRecord(file, extraData = {}) {
  const fd = new FormData();
  fd.append('file', file);
  Object.entries(extraData).forEach(([k, v]) => fd.append(k, v));
  return upload('/medical-records/upload', fd);
}

/** 删除病历 */
export function deleteMedicalRecord(id) {
  return del(`/medical-records/${id}`);
}

/** 获取 OCR/AI 分析状态 */
export function getAnalysisStatus(id) {
  return get(`/medical-records/${id}/analysis`);
}

/** 生成分享链接 */
export function shareMedicalRecord(id) {
  return post(`/medical-records/${id}/share`);
}

/** 获取分享状态 */
export function getShareStatus(id) {
  return get(`/medical-records/${id}/share-status`);
}

/** 撤销分享 */
export function revokeShare(id) {
  return del(`/medical-records/${id}/share`);
}

/** 通过分享令牌查看病历 */
export function viewSharedRecord(token) {
  return get(`/share/${token}`);
}

/**
 * 下载病历 PDF
 * @param {number} id - 病历ID
 * @param {boolean} includeAnalysis - 是否包含AI分析
 */
export async function downloadPdf(id, includeAnalysis = false) {
  const token = localStorage.getItem(AUTH_TOKEN_KEY);
  const url = new URL(`${API_CONFIG.BASE_URL}/medical-records/${id}/print`);
  if (includeAnalysis) {
    url.searchParams.set('includeAnalysis', 'true');
  }

  const response = await fetch(url.toString(), {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  });

  if (!response.ok) {
    if (response.status === 401) {
      window.dispatchEvent(new CustomEvent('auth:unauthorized'));
      throw new Error('登录已失效');
    }
    let msg = '下载失败';
    try {
      const data = await response.json();
      msg = data?.message || msg;
    } catch { /* not JSON */ }
    throw new Error(msg);
  }

  const blob = await response.blob();
  const disposition = response.headers.get('Content-Disposition') || '';
  const match = disposition.match(/filename="(.+)"/);
  const filename = match ? decodeURIComponent(match[1]) : `病历_${id}.pdf`;

  // Trigger download
  const blobUrl = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = blobUrl;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(blobUrl);
}
