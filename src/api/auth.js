import { request } from './http'

export function login(credentials) {
  return request('/api/v1/auth/login', {
    method: 'POST',
    body: credentials,
    auth: false
  })
}

export function getCurrentUser() {
  return request('/api/v1/auth/me')
}

export function logout() {
  return request('/api/v1/auth/logout', { method: 'POST' })
}
