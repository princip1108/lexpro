import { request } from './http'

export function listCases(filters = {}) {
  const query = new URLSearchParams()
  Object.entries(filters).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      query.set(key, String(value))
    }
  })
  const suffix = query.size ? `?${query.toString()}` : ''
  return request(`/api/v1/cases${suffix}`)
}

export function getCase(caseId) {
  return request(`/api/v1/cases/${caseId}`)
}

export function createCase(payload) {
  return request('/api/v1/cases', { method: 'POST', body: payload })
}

export function updateCase(caseId, payload) {
  return request(`/api/v1/cases/${caseId}`, { method: 'PUT', body: payload })
}

export function listCaseParties(caseId) {
  return request(`/api/v1/cases/${caseId}/parties`)
}

export function listCaseAssignments(caseId) {
  return request(`/api/v1/cases/${caseId}/assignments`)
}
