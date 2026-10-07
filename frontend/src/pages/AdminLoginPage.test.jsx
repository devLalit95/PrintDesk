import { ThemeProvider } from '@mui/material/styles'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import theme from '../theme'
import { loginAdmin } from '../services/adminApi'
import useAuthStore from '../store/authStore'
import AdminLoginPage from './AdminLoginPage'

vi.mock('../services/adminApi', () => ({
  loginAdmin: vi.fn(),
}))

vi.mock('../services/logger', () => ({
  logUiError: vi.fn(),
}))

describe('admin login page', () => {
  afterEach(() => {
    useAuthStore.getState().clearSession()
    vi.clearAllMocks()
  })

  it('sends credentials to the documented login API and stores only the returned session', async () => {
    const user = userEvent.setup()
    loginAdmin.mockResolvedValue({
      accessToken: 'signed-jwt',
      tokenType: 'Bearer',
      expiresAt: '2099-01-01T00:00:00Z',
    })
    render(
      <ThemeProvider theme={theme}>
        <MemoryRouter>
          <AdminLoginPage />
        </MemoryRouter>
      </ThemeProvider>,
    )

    await user.type(screen.getByRole('textbox', { name: 'Username' }), 'lalit')
    await user.type(screen.getByLabelText(/Password/), 'test-password')
    await user.click(screen.getByRole('button', { name: 'Sign in' }))

    expect(loginAdmin).toHaveBeenCalledWith({ username: 'lalit', password: 'test-password' })
    expect(useAuthStore.getState()).toMatchObject({
      accessToken: 'signed-jwt',
      expiresAt: '2099-01-01T00:00:00Z',
    })
  })
})
