import { request } from './http'

export const listRecommendations = (caseId) => request(`/api/v1/cases/${caseId}/recommendations`)
export const getRecommendation = (caseId, recommendId) => request(`/api/v1/cases/${caseId}/recommendations/${recommendId}`)
export const createRecommendationAnalysis = (caseId, payload) => request(`/api/v1/cases/${caseId}/recommendation-analyses`, { method: 'POST', body: payload, timeout: 70000 })
export const createRecommendation = (caseId, payload) => request(`/api/v1/cases/${caseId}/recommendations`, { method: 'POST', body: payload, timeout: 60000 })
export const getTypicalCase = (typicalCaseId) => request(`/api/v1/typical-cases/${typicalCaseId}`)
export function listTypicalCases(filters = {}) {
  const query = new URLSearchParams()
  Object.entries(filters).forEach(([key, value]) => {
    if (value !== null && value !== undefined && value !== '') query.set(key, String(value))
  })
  return request(`/api/v1/typical-cases?${query}`)
}
export const favoriteTypicalCase = (typicalCaseId) => request(`/api/v1/typical-cases/${typicalCaseId}/favorite`, { method: 'PUT' })
export const unfavoriteTypicalCase = (typicalCaseId) => request(`/api/v1/typical-cases/${typicalCaseId}/favorite`, { method: 'DELETE' })
