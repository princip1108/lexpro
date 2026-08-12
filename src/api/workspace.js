import { request } from './http'

function queryString(filters) {
  const query = new URLSearchParams()
  Object.entries(filters).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') query.set(key, String(value))
  })
  return query.size ? `?${query.toString()}` : ''
}

export function listKnowledge(filters = {}) {
  return request(`/api/v1/knowledge-contents${queryString(filters)}`)
}

export function getKnowledge(contentId) {
  return request(`/api/v1/knowledge-contents/${contentId}`)
}

export function getKnowledgeStatistics() {
  return request('/api/v1/knowledge-contents/statistics')
}

export function createKnowledge(payload) {
  return request('/api/v1/knowledge-contents', { method: 'POST', body: payload })
}

export function updateKnowledge(contentId, payload) {
  return request(`/api/v1/knowledge-contents/${contentId}`, { method: 'PUT', body: payload })
}

export function listWorkTasks(filters = {}) {
  return request(`/api/v1/work-tasks${queryString(filters)}`)
}

export function getWorkTask(taskId) {
  return request(`/api/v1/work-tasks/${taskId}`)
}

export function createWorkTask(payload) {
  return request('/api/v1/work-tasks', { method: 'POST', body: payload })
}

export function updateWorkTask(taskId, payload) {
  return request(`/api/v1/work-tasks/${taskId}`, { method: 'PUT', body: payload })
}

export function getDashboard() {
  return request('/api/v1/dashboard')
}
