import { request } from './http'

export const listReportTemplates = (filters = {}) => {
  const query = new URLSearchParams()
  Object.entries(filters).forEach(([key, value]) => value && query.set(key, value))
  return request(`/api/v1/report-templates${query.size ? `?${query}` : ''}`)
}
export const getReportTemplate = (templateId) => request(`/api/v1/report-templates/${templateId}`)
export const createReportTemplate = (payload) => request('/api/v1/report-templates', { method: 'POST', body: payload })
export const activateReportTemplate = (templateId) => request(`/api/v1/report-templates/${templateId}/activation`, { method: 'PUT' })
export const disableReportTemplate = (templateId) => request(`/api/v1/report-templates/${templateId}/disablement`, { method: 'PUT' })
