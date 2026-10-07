import { ThemeProvider } from '@mui/material/styles'
import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import theme from '../theme'
import useAuthStore from '../store/authStore'
import AdminPage from './AdminPage'
import {
  cancelAdminOrder,
  getAdminDashboardStats,
  getAdminOrder,
  getAdminOrders,
} from '../services/adminApi'

vi.mock('../services/adminApi', () => ({
  cancelAdminOrder: vi.fn(),
  downloadAdminDocument: vi.fn(),
  getAdminDashboardStats: vi.fn(),
  getAdminOrder: vi.fn(),
  getAdminOrders: vi.fn(),
}))

vi.mock('../services/logger', () => ({
  logUiError: vi.fn(),
}))

const order = {
  id: 'f35ddc18-d6db-47ae-a2d7-3ed8b403fdf8',
  documentId: '13c0774b-a911-40e3-a54a-1f3d507fb1f9',
  token: '7KX3M9P2QW6A',
  fileName: 'project.pdf',
  contentType: 'application/pdf',
  sizeBytes: 2048,
  pageCount: 3,
  printType: 'BLACK_AND_WHITE',
  copies: 2,
  totalPages: 6,
  paperSize: 'A4',
  orientation: 'portrait',
  doubleSided: false,
  pricePerPage: 2.5,
  totalAmount: 15,
  currency: 'INR',
  status: 'PENDING',
  createdAt: '2026-10-08T02:59:10Z',
  updatedAt: '2026-10-08T02:59:10Z',
  printedAt: null,
  attempts: [],
}

function renderAdmin() {
  return render(
    <ThemeProvider theme={theme}>
      <MemoryRouter>
        <AdminPage />
      </MemoryRouter>
    </ThemeProvider>,
  )
}

describe('admin order dashboard', () => {
  afterEach(() => {
    useAuthStore.getState().clearSession()
    vi.clearAllMocks()
  })

  it('loads statistics and order results, then shows full details', async () => {
    useAuthStore.getState().setSession({
      accessToken: 'admin-token',
      expiresAt: '2099-01-01T00:00:00Z',
    })
    getAdminOrders.mockResolvedValue({
      items: [order],
      page: 0,
      size: 10,
      totalItems: 1,
      totalPages: 1,
      first: true,
      last: true,
    })
    getAdminDashboardStats.mockResolvedValue({
      totalOrders: 1,
      pendingOrders: 1,
      inProgressOrders: 0,
      printedOrders: 0,
      failedOrders: 0,
      cancelledOrders: 0,
    })
    getAdminOrder.mockResolvedValue(order)

    renderAdmin()

    expect(await screen.findByText('project.pdf')).toBeInTheDocument()
    expect(screen.getByText('Waiting for review')).toBeInTheDocument()
    expect(screen.getByText('Cancelled')).toBeInTheDocument()
    expect(screen.getByText('Print and retry are unavailable until the authenticated Print Agent and durable print queue are implemented. Orders will not be marked as printing from this dashboard.')).toBeInTheDocument()

    await userEvent.click(screen.getByRole('button', { name: 'Details' }))
    expect(await screen.findByRole('heading', { name: 'Print request details' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Download document' })).toBeInTheDocument()
    expect(screen.getByText('No print attempts have been recorded.')).toBeInTheDocument()
    expect(getAdminOrder).toHaveBeenCalledWith(order.id, expect.any(AbortSignal))
  })

  it('requires confirmation before cancelling a pending order and refreshes status', async () => {
    const user = userEvent.setup()
    const cancelledOrder = { ...order, status: 'CANCELLED' }
    useAuthStore.getState().setSession({
      accessToken: 'admin-token',
      expiresAt: '2099-01-01T00:00:00Z',
    })
    getAdminOrders.mockResolvedValue({
      items: [order],
      page: 0,
      size: 10,
      totalItems: 1,
      totalPages: 1,
      first: true,
      last: true,
    })
    getAdminDashboardStats.mockResolvedValue({
      totalOrders: 1,
      pendingOrders: 1,
      inProgressOrders: 0,
      printedOrders: 0,
      failedOrders: 0,
      cancelledOrders: 0,
    })
    getAdminOrder.mockResolvedValue(order)
    cancelAdminOrder.mockResolvedValue(cancelledOrder)

    renderAdmin()
    await user.click(await screen.findByRole('button', { name: 'Details' }))
    await user.click(await screen.findByRole('button', { name: 'Cancel order' }))
    expect(screen.getByRole('heading', { name: 'Cancel this print request?' })).toBeInTheDocument()
    expect(cancelAdminOrder).not.toHaveBeenCalled()

    await user.click(screen.getByRole('button', { name: 'Confirm cancellation' }))

    await waitFor(() => expect(cancelAdminOrder).toHaveBeenCalledWith(order.id))
    expect(await screen.findByText(`Order ${order.token} was cancelled.`)).toBeInTheDocument()
    await waitFor(() => expect(getAdminOrders).toHaveBeenCalledTimes(2))
  })

  it('shows a load failure instead of presenting it as an empty order list', async () => {
    useAuthStore.getState().setSession({
      accessToken: 'admin-token',
      expiresAt: '2099-01-01T00:00:00Z',
    })
    getAdminOrders.mockRejectedValue(new Error('Unable to load orders'))
    getAdminDashboardStats.mockResolvedValue({})

    renderAdmin()

    expect(await screen.findByRole('heading', { name: 'Dashboard data unavailable' })).toBeInTheDocument()
    expect(screen.queryByText('No print requests match these filters.')).not.toBeInTheDocument()
  })
})
