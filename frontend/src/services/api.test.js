import { afterEach, describe, expect, it, vi } from 'vitest'
import { AxiosError } from 'axios'
import { api, getApiErrorMessage } from './api'
import { logApiFailure } from './logger'

vi.mock('./logger', () => ({
  logApiFailure: vi.fn(),
}))

describe('frontend API client', () => {
  afterEach(() => {
    vi.restoreAllMocks()
    vi.clearAllMocks()
  })

  it('extracts a safe message and validation fields from Problem Details', () => {
    expect(getApiErrorMessage({
      response: {
        data: {
          detail: 'Request validation failed.',
          fieldErrors: { copies: 'Copies must be positive.' },
        },
      },
    })).toEqual({
      message: 'Request validation failed.',
      fieldErrors: { copies: 'Copies must be positive.' },
    })
  })

  it('logs failed responses with request ID and stable code only', async () => {
    const originalAdapter = api.defaults.adapter
    api.defaults.adapter = async (config) => {
      const response = {
        status: 401,
        statusText: 'Unauthorized',
        headers: {},
        config,
        data: { code: 'INVALID_ADMIN_CREDENTIALS' },
      }
      throw new AxiosError(
        'Request failed with status code 401',
        AxiosError.ERR_BAD_REQUEST,
        config,
        undefined,
        response,
      )
    }

    await expect(api.post('/api/admin/login', { username: 'user', password: 'private' }))
      .rejects.toMatchObject({ response: { status: 401 } })

    expect(logApiFailure).toHaveBeenCalledWith(expect.objectContaining({
      method: 'POST',
      status: 401,
      code: 'INVALID_ADMIN_CREDENTIALS',
    }))
    expect(logApiFailure.mock.calls[0][0].requestId).toEqual(expect.any(String))
    api.defaults.adapter = originalAdapter
  })
})
