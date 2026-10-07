import { afterEach, describe, expect, it, vi } from 'vitest'
import { logApiFailure, logUiError } from './logger'

describe('frontend diagnostic logging', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('logs API failure metadata without request bodies or credentials', () => {
    const error = vi.spyOn(console, 'error').mockImplementation(() => {})

    logApiFailure({
      method: 'POST',
      requestId: 'req-123',
      status: 401,
      code: 'INVALID_ADMIN_CREDENTIALS',
    })

    expect(error).toHaveBeenCalledOnce()
    expect(error.mock.calls[0][0]).toMatchObject({
      level: 'error',
      component: 'api',
      event: 'request_failed',
      method: 'POST',
      requestId: 'req-123',
      status: 401,
      code: 'INVALID_ADMIN_CREDENTIALS',
    })
    expect(JSON.stringify(error.mock.calls[0])).not.toMatch(/password|accessToken|authorization/i)
  })

  it('logs UI bug context without exception messages that may contain user data', () => {
    const error = vi.spyOn(console, 'error').mockImplementation(() => {})

    logUiError('order_submit_failed', { message: 'secret document contents' })

    expect(error.mock.calls[0][0]).toMatchObject({
      level: 'error',
      component: 'ui',
      event: 'order_submit_failed',
    })
    expect(JSON.stringify(error.mock.calls[0])).not.toContain('secret document contents')
  })
})
