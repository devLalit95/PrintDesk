import { create } from 'zustand'

const emptySession = { accessToken: null, expiresAt: null }
const maxTimeout = 2_147_483_647
let expirationTimer

function clearExpirationTimer() {
  if (expirationTimer) {
    clearTimeout(expirationTimer)
    expirationTimer = undefined
  }
}

function scheduleExpiration(expiryTime, set) {
  const remainingTime = expiryTime - Date.now()
  expirationTimer = setTimeout(() => {
    if (Date.now() >= expiryTime) {
      expirationTimer = undefined
      set(emptySession)
      return
    }
    scheduleExpiration(expiryTime, set)
  }, Math.min(remainingTime, maxTimeout))
}

const useAuthStore = create((set) => ({
  accessToken: null,
  expiresAt: null,
  setSession: (session) => {
    const expiryTime = Date.parse(session?.expiresAt)
    if (!session?.accessToken || !Number.isFinite(expiryTime) || expiryTime <= Date.now()) {
      throw new Error('The administrator login response contained an invalid session.')
    }

    clearExpirationTimer()
    set({ accessToken: session.accessToken, expiresAt: session.expiresAt })
    scheduleExpiration(expiryTime, set)
  },
  clearSession: () => {
    clearExpirationTimer()
    set(emptySession)
  },
}))

export default useAuthStore
