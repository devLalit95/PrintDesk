import axios from 'axios'
import { logApiFailure } from './logger'

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '',
  timeout: 30_000,
  headers: {
    Accept: 'application/json',
  },
})

api.interceptors.request.use((config) => {
  const requestId = globalThis.crypto?.randomUUID?.()
    ?? `req-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`
  config.headers.set('X-Request-ID', requestId)
  return config
})

api.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error.response?.status
    const code = error.response?.data?.code
    const method = error.config?.method?.toUpperCase()
    const requestId = error.config?.headers?.['X-Request-ID']

    logApiFailure({ method, requestId, status, code })
    return Promise.reject(error)
  },
)

export function getApiErrorMessage(error, fallback = 'Something went wrong. Please try again.') {
  const detail = error?.response?.data?.detail
  const fieldErrors = error?.response?.data?.fieldErrors

  return {
    message: typeof detail === 'string' ? detail : fallback,
    fieldErrors: fieldErrors && typeof fieldErrors === 'object' ? fieldErrors : {},
  }
}

export default api
export { api }
