import { request } from './http'

export const listOrganizations = () => request('/api/v1/organizations/tree')
export const listUsers = (page = 1, size = 100) => request(`/api/v1/users?page=${page}&size=${size}`)
export const listRoles = () => request('/api/v1/roles')
export const listPermissions = () => request('/api/v1/permissions')
export const createUser = (payload) => request('/api/v1/users', { method: 'POST', body: payload })
export const updateUserStatus = (userId, status) => request(`/api/v1/users/${userId}/status`, { method: 'PATCH', body: { status } })
export const resetUserPassword = (userId, newPassword) => request(`/api/v1/users/${userId}/password`, { method: 'PUT', body: { newPassword } })
