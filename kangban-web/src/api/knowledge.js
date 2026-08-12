import { del, get, post, upload } from './request.js';

function adminHeaders(token) {
  return token ? { 'X-Knowledge-Admin-Token': token } : {};
}

export function getKnowledgeDocuments(token, status) {
  return get('/admin/knowledge/documents', { params: { status }, headers: adminHeaders(token) });
}

export function getKnowledgeJob(token, id) {
  return get(`/admin/knowledge/jobs/${id}`, { headers: adminHeaders(token) });
}

export function getKnowledgeChunks(token, id) {
  return get(`/admin/knowledge/documents/${id}/chunks`, { headers: adminHeaders(token) });
}

export function uploadKnowledgeDocument(token, file, fields = {}) {
  const form = new FormData();
  form.append('file', file);
  Object.entries(fields).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') form.append(key, value);
  });
  return upload('/admin/knowledge/documents', form, { headers: adminHeaders(token) });
}

export function submitKnowledgeReview(token, id) {
  return post(`/admin/knowledge/documents/${id}/submit-review`, undefined, { headers: adminHeaders(token) });
}

export function publishKnowledgeDocument(token, id, reviewNote) {
  return post(`/admin/knowledge/documents/${id}/publish`, undefined, {
    params: { reviewNote }, headers: adminHeaders(token),
  });
}

export function revokeKnowledgeDocument(token, id, reason) {
  return post(`/admin/knowledge/documents/${id}/revoke`, undefined, {
    params: { reason }, headers: adminHeaders(token),
  });
}

export function reindexKnowledgeDocument(token, id) {
  return post(`/admin/knowledge/documents/${id}/reindex`, undefined, { headers: adminHeaders(token) });
}

export function deleteKnowledgeDocument(token, id) {
  return del(`/admin/knowledge/documents/${id}`, { headers: adminHeaders(token) });
}

export function searchKnowledge(token, query) {
  return get('/admin/knowledge/search', { params: { q: query }, headers: adminHeaders(token) });
}
