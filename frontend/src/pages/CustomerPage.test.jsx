import { ThemeProvider } from '@mui/material/styles'
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { afterEach, describe, expect, it, vi } from 'vitest'
import theme from '../theme'
import { createPrintOrder, estimatePrintOrder, getPrintOrderStatus, uploadDocument } from '../services/customerApi'
import CustomerPage from './CustomerPage'
import TrackPage from './TrackPage'

vi.mock('../services/customerApi', () => ({
  uploadDocument: vi.fn(),
  estimatePrintOrder: vi.fn(),
  createPrintOrder: vi.fn(),
  getPrintOrderStatus: vi.fn(),
}))

vi.mock('../services/logger', () => ({
  logUiError: vi.fn(),
}))

function renderWithApp(element, initialEntries = ['/']) {
  return render(
    <ThemeProvider theme={theme}>
      <MemoryRouter initialEntries={initialEntries}>{element}</MemoryRouter>
    </ThemeProvider>,
  )
}

describe('customer printing flow', () => {
  afterEach(() => {
    vi.clearAllMocks()
  })

  it('uploads a real file, requests a server quote, and creates an order', async () => {
    const user = userEvent.setup()
    uploadDocument.mockResolvedValue({
      documentId: 'doc-123',
      fileName: 'report.pdf',
      contentType: 'application/pdf',
      sizeBytes: 2048,
      pageCount: 3,
    })
    estimatePrintOrder.mockResolvedValue({
      printType: 'BLACK_AND_WHITE',
      documentPages: 3,
      copies: 1,
      totalPages: 3,
      pricePerPage: 2.5,
      totalAmount: 7.5,
      currency: 'INR',
    })
    createPrintOrder.mockResolvedValue({
      token: '7KX3M9P2QW6A',
      fileName: 'report.pdf',
      pageCount: 3,
      printType: 'BLACK_AND_WHITE',
      copies: 1,
      totalPages: 3,
      totalAmount: 7.5,
      currency: 'INR',
      status: 'PENDING',
    })

    const { container } = renderWithApp(<CustomerPage />)
    expect(within(screen.getByRole('region', { name: 'Document upload' })).getAllByRole('button')).toHaveLength(1)
    const file = new File(['pdf-content'], 'report.pdf', { type: 'application/pdf' })
    await user.upload(container.querySelector('input[type="file"]'), file)

    expect(await screen.findAllByText('report.pdf')).toHaveLength(2)
    await waitFor(() => expect(estimatePrintOrder).toHaveBeenCalledWith({
      documentId: 'doc-123',
      printType: 'BLACK_AND_WHITE',
      copies: 1,
    }, expect.any(AbortSignal)))
    expect(await screen.findByText('₹7.50')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Submit print request' }))
    expect(await screen.findByLabelText('Order token 7KX3M9P2QW6A')).toBeInTheDocument()
    expect(createPrintOrder).toHaveBeenCalledWith({
      documentId: 'doc-123',
      printType: 'BLACK_AND_WHITE',
      copies: 1,
      paperSize: 'A4',
      orientation: 'portrait',
      doubleSided: false,
    })
  })

  it('rejects unsupported local file types before upload', async () => {
    const user = userEvent.setup({ applyAccept: false })
    const { container } = renderWithApp(<CustomerPage />)
    const file = new File(['script'], 'payload.exe', { type: 'application/octet-stream' })

    await user.upload(container.querySelector('input[type="file"]'), file)

    expect(await screen.findByText('Choose a PDF, DOCX, JPG, or PNG file.')).toBeInTheDocument()
    expect(uploadDocument).not.toHaveBeenCalled()
  })

  it('looks up an order using the token from the track URL', async () => {
    const user = userEvent.setup()
    getPrintOrderStatus.mockResolvedValue({
      token: '7KX3M9P2QW6A',
      status: 'PRINTED',
      pageCount: 3,
      copies: 2,
      totalAmount: 15,
    })
    renderWithApp(<TrackPage />, ['/track?token=7kx3m9p2qw6a'])

    expect(screen.getByRole('textbox', { name: 'Order token' })).toHaveValue('7KX3M9P2QW6A')
    await user.click(screen.getByRole('button', { name: 'Check status' }))

    expect(await screen.findByText('PRINTED')).toBeInTheDocument()
    expect(getPrintOrderStatus).toHaveBeenCalledWith('7KX3M9P2QW6A')
  })
})
