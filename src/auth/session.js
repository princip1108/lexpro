import { computed, ref } from 'vue'

const STORAGE_KEY = 'lexpro.auth.session'

function readStoredSession(storage) {
  try {
    const value = storage.getItem(STORAGE_KEY)
    return value ? JSON.parse(value) : null
  } catch {
    storage.removeItem(STORAGE_KEY)
    return null
  }
}

function decodeExpiration(accessToken) {
  try {
    const encodedPayload = accessToken.split('.')[1]
    const normalized = encodedPayload.replace(/-/g, '+').replace(/_/g, '/')
    const padded = normalized.padEnd(Math.ceil(normalized.length / 4) * 4, '=')
    const payload = JSON.parse(atob(padded))
    return Number.isFinite(payload.exp) ? payload.exp * 1000 : null
  } catch {
    return null
  }
}

function loadSession() {
  return readStoredSession(sessionStorage) ?? readStoredSession(localStorage)
}

const session = ref(loadSession())

export const currentUser = computed(() => session.value?.user ?? null)

export function getAccessToken() {
  return hasValidSession() ? session.value.accessToken : null
}

export function hasValidSession() {
  if (!session.value?.accessToken || !session.value?.expiresAt) {
    return false
  }

  const responseExpiration = Date.parse(session.value.expiresAt)
  const tokenExpiration = decodeExpiration(session.value.accessToken)
  if (!Number.isFinite(responseExpiration) || tokenExpiration === null) {
    clearSession()
    return false
  }
  if (Math.min(responseExpiration, tokenExpiration) <= Date.now()) {
    clearSession()
    return false
  }
  return true
}

export function saveSession(loginResponse, persistent) {
  clearSession()
  const value = {
    accessToken: loginResponse.accessToken,
    expiresAt: loginResponse.expiresAt,
    user: loginResponse.user
  }
  const storage = persistent ? localStorage : sessionStorage
  storage.setItem(STORAGE_KEY, JSON.stringify(value))
  session.value = value
}

export function clearSession() {
  sessionStorage.removeItem(STORAGE_KEY)
  localStorage.removeItem(STORAGE_KEY)
  session.value = null
}

export function hasPermission(permission) {
  return currentUser.value?.permissions?.includes(permission) ?? false
}

window.addEventListener('storage', (event) => {
  if (event.key === STORAGE_KEY) {
    session.value = loadSession()
  }
})
