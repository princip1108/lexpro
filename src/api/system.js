import { request } from './http'

export const getModelConfiguration = () => request('/api/v1/system/model-configuration')
export const createModelConfiguration = (body) => request('/api/v1/system/model-configurations', {method:'POST',body})
export const updateModelConfiguration = (id,body) => request(`/api/v1/system/model-configurations/${id}`, {method:'PUT',body})
export const activateModelConfiguration = (id) => request(`/api/v1/system/model-configurations/${id}/activation`, {method:'POST'})
export const deleteModelConfiguration = (id) => request(`/api/v1/system/model-configurations/${id}`, {method:'DELETE'})
export const listOperationLogs = () => request('/api/v1/system/operation-logs?limit=200')
