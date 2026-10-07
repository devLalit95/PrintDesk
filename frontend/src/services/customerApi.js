import api from './api'

export async function uploadDocument(file, onUploadProgress, signal) {
  const formData = new FormData()
  formData.append('file', file)
  const response = await api.post('/api/documents/upload', formData, {
    signal,
    onUploadProgress: (event) => {
      if (event.total) onUploadProgress(Math.round((event.loaded * 100) / event.total))
    },
  })
  return response.data
}

export async function estimatePrintOrder(request, signal) {
  const response = await api.post('/api/print-orders/estimate', request, { signal })
  return response.data
}

export async function createPrintOrder(request) {
  const response = await api.post('/api/print-orders', request)
  return response.data
}

export async function getPrintOrderStatus(token, signal) {
  const response = await api.get(`/api/print-orders/${encodeURIComponent(token.trim())}`, { signal })
  return response.data
}
