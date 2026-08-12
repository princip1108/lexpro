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
