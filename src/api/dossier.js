import { request } from './http'

export function listDossierFiles(caseId, includeDeleted = false) {
  return request(`/api/v1/cases/${caseId}/dossier/files?includeDeleted=${includeDeleted}`)
}

export function uploadDossierFile(caseId, file, folderId = null, tagIds = []) {
  const body = new FormData()
  body.append('file', file)
  if (folderId) body.append('folderId', String(folderId))
  tagIds.forEach((tagId) => body.append('tagId', String(tagId)))
  return request(`/api/v1/cases/${caseId}/dossier/files`, { method: 'POST', body, timeout: 60000 })
}

export function downloadDossierFile(caseId, dossierId) {
  return request(`/api/v1/cases/${caseId}/dossier/files/${dossierId}/content`, { responseType: 'blob', timeout: 60000 })
}

export function startDocumentParse(caseId, dossierId) {
  return request(`/api/v1/cases/${caseId}/dossier/files/${dossierId}/parse-jobs`, { method: 'POST', body: {} })
}

export function listParseResults(caseId, dossierId) {
  return request(`/api/v1/cases/${caseId}/dossier/files/${dossierId}/parse-results`)
}

export function getParsedDocument(caseId, docId) {
  return request(`/api/v1/cases/${caseId}/documents/${docId}`)
}

export const listDossierFolders = (caseId) => request(`/api/v1/cases/${caseId}/dossier/folders`)
export const createDossierFolder = (caseId, payload) => request(`/api/v1/cases/${caseId}/dossier/folders`, { method: 'POST', body: payload })
export const listDossierTags = (caseId) => request(`/api/v1/cases/${caseId}/dossier/tags`)
export const createDossierTag = (caseId, payload) => request(`/api/v1/cases/${caseId}/dossier/tags`, { method: 'POST', body: payload })
export const updateDossierFile = (caseId, dossierId, payload) => request(`/api/v1/cases/${caseId}/dossier/files/${dossierId}`, { method: 'PUT', body: payload })
export const deleteDossierFile = (caseId, dossierId) => request(`/api/v1/cases/${caseId}/dossier/files/${dossierId}`, { method: 'DELETE' })
export const restoreDossierFile = (caseId, dossierId) => request(`/api/v1/cases/${caseId}/dossier/files/${dossierId}/restore`, { method: 'POST' })
