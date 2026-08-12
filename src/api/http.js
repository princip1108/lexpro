import { clearSession, getAccessToken } from '../auth/session'

const DEFAULT_TIMEOUT_MS = 15000
const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || 'http://127.0.0.1:8080').replace(/\/$/, '')

export const UNAUTHORIZED_EVENT = 'lexpro:unauthorized'

export class ApiError extends Error {
  constructor(message, status = 0, problem = null) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.errorCode = problem?.errorCode ?? null
    this.fieldErrors = problem?.fieldErrors ?? null
  }
}

export async function request(path, options = {}) {
  const controller = new AbortController()
  const timeoutId = window.setTimeout(() => controller.abort(), options.timeout ?? DEFAULT_TIMEOUT_MS)
  const headers = new Headers(options.headers)
  headers.set('Accept', 'application/json')
  headers.set('X-Request-Id', crypto.randomUUID())

  const isFormData = options.body instanceof FormData
  if (options.body !== undefined && !isFormData) {
    headers.set('Content-Type', 'application/json')
  }
  if (options.auth !== false) {
    const accessToken = getAccessToken()
    if (accessToken) {
      headers.set('Authorization', `Bearer ${accessToken}`)
    }
  }

  try {
    const response = await fetch(`${API_BASE_URL}${path}`, {
      method: options.method ?? 'GET',
      headers,
      body: options.body === undefined ? undefined : (isFormData ? options.body : JSON.stringify(options.body)),
      signal: controller.signal,
      credentials: 'omit'
    })

    if (!response.ok) {
      const problem = await readBody(response)
      if (response.status === 401 && options.auth !== false) {
        clearSession()
        window.dispatchEvent(new Event(UNAUTHORIZED_EVENT))
      }
      throw new ApiError(problem?.detail || '请求失败', response.status, problem)
    }
    if (response.status === 204) return null
    if (options.responseType === 'blob') {
      return { blob: await response.blob(), fileName: responseFileName(response) }
    }
    return readBody(response)
  } catch (error) {
    if (error instanceof ApiError) {
      throw error
    }
    if (error.name === 'AbortError') {
      throw new ApiError('请求超时，请稍后重试')
    }
    throw new ApiError('无法连接后端服务，请确认后端已经启动')
  } finally {
    window.clearTimeout(timeoutId)
  }
}

function responseFileName(response) {
  const disposition = response.headers.get('content-disposition') || ''
  const encoded = disposition.match(/filename\*=UTF-8''([^;]+)/i)?.[1]
  const plain = disposition.match(/filename="?([^";]+)"?/i)?.[1]
  try { return decodeURIComponent(encoded || plain || '') }
  catch { return plain || '' }
}

async function readBody(response) {
  const contentType = response.headers.get('content-type') || ''
  if (!contentType.includes('json')) {
    return null
  }
  try {
    return await response.json()
  } catch {
    return null
  }
}
