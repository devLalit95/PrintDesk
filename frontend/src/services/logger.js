function write(level, component, event, metadata = {}) {
  const entry = {
    timestamp: new Date().toISOString(),
    level,
    component,
    event,
    ...metadata,
  }

  const output = level === 'error' ? console.error : console.info
  output(entry)
}

export function logApiFailure({ method, requestId, status, code }) {
  write('error', 'api', 'request_failed', {
    method,
    requestId,
    status,
    code,
  })
}

export function logUiError(event, error) {
  const status = Number.isInteger(error?.response?.status)
    ? error.response.status
    : undefined
  const code = typeof error?.response?.data?.code === 'string'
    ? error.response.data.code
    : undefined

  write('error', 'ui', event, { status, code })
}
