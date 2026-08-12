import { request } from './http'

export const startEntityRecognition = (caseId, docId) => request(`/api/v1/cases/${caseId}/documents/${docId}/entity-recognition-jobs`, { method: 'POST' })
export const getEntityRecognitionJob = (caseId, docId, requestId) => request(`/api/v1/cases/${caseId}/documents/${docId}/entity-recognition-jobs/${requestId}`)
export const listEntityResults = (caseId, docId) => request(`/api/v1/cases/${caseId}/documents/${docId}/entity-results`)
export const getEntityResult = (caseId, docId, resultId) => request(`/api/v1/cases/${caseId}/documents/${docId}/entity-results/${resultId}`)
export const confirmEntityResult = (caseId, docId, resultId, finalEntities) => request(`/api/v1/cases/${caseId}/documents/${docId}/entity-results/${resultId}/confirmation`, { method: 'PUT', body: { finalEntities } })

export const startLegalElementRecognition = (caseId, docId) => request(`/api/v1/cases/${caseId}/documents/${docId}/legal-element-jobs`, { method: 'POST' })
export const getLegalElementJob = (caseId, docId, requestId) => request(`/api/v1/cases/${caseId}/documents/${docId}/legal-element-jobs/${requestId}`)
export const listLegalElementResults = (caseId, docId) => request(`/api/v1/cases/${caseId}/documents/${docId}/legal-element-results`)
export const getLegalElementResult = (caseId, docId, resultId) => request(`/api/v1/cases/${caseId}/documents/${docId}/legal-element-results/${resultId}`)
export const confirmLegalElementResult = (caseId, docId, resultId, finalElements) => request(`/api/v1/cases/${caseId}/documents/${docId}/legal-element-results/${resultId}/confirmation`, { method: 'PUT', body: { finalElements } })

export const startCaseSummary = (caseId, payload) => request(`/api/v1/cases/${caseId}/summary-jobs`, { method: 'POST', body: payload })
export const getCaseSummaryJob = (caseId, requestId) => request(`/api/v1/cases/${caseId}/summary-jobs/${requestId}`)
export const listCaseSummaries = (caseId, summaryType) => request(`/api/v1/cases/${caseId}/summaries?summaryType=${summaryType}`)
export const getCaseSummary = (caseId, summaryId) => request(`/api/v1/cases/${caseId}/summaries/${summaryId}`)
export const confirmCaseSummary = (caseId, summaryId) => request(`/api/v1/cases/${caseId}/summaries/${summaryId}/confirmation`, { method: 'PUT' })
