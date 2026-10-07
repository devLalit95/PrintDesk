import { afterEach, describe, expect, it, vi } from 'vitest'
import useAuthStore from './authStore'

describe('administrator session store', () => {
  afterEach(() => {
    useAuthStore.getState().clearSession()
    vi.useRealTimers()
  })

  it('clears the in-memory token when its expiration time is reached', () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-10-08T12:00:00Z'))
    useAuthStore.getState().setSession({
      accessToken: 'short-lived-token',
      expiresAt: '2026-10-08T12:00:05Z',
    })

    vi.advanceTimersByTime(5000)

    expect(useAuthStore.getState()).toMatchObject({
      accessToken: null,
      expiresAt: null,
    })
  })
})
