import { request } from './http'

export const listRecommendations = (caseId) => request(`/api/v1/cases/${caseId}/recommendations`)
export const getRecommendation = (caseId, recommendId) => request(`/api/v1/cases/${caseId}/recommendations/${recommendId}`)
export const createRecommendation = (caseId, payload) => request(`/api/v1/cases/${caseId}/recommendations`, { method: 'POST', body: payload, timeout: 60000 })
