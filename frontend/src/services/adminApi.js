import api from './api'
import useAuthStore from '../store/authStore'

function adminConfig(config = {}) {
  const accessToken = useAuthStore.getState().accessToken
  if (!accessToken) throw new Error('An administrator session is required.')

  return {
    ...config,
    headers: {
      ...config.headers,
      Authorization: `Bearer ${accessToken}`,
    },
  }
}

export async function loginAdmin(credentials) {
  const response = await api.post('/api/admin/login', credentials)
  return response.data
}

export async function getAdminDashboardStats(signal) {
  const response = await api.get('/api/admin/dashboard/stats', adminConfig({ signal }))
  return response.data
}

export async function getAdminOrders(params, signal) {
  const response = await api.get('/api/admin/print-orders', adminConfig({ params, signal }))
  return response.data
}

export async function getAdminOrder(orderId, signal) {
  const response = await api.get(
    `/api/admin/print-orders/${encodeURIComponent(orderId)}`,
    adminConfig({ signal }),
  )
  return response.data
}

export async function cancelAdminOrder(orderId) {
  const response = await api.post(
    `/api/admin/print-orders/${encodeURIComponent(orderId)}/cancel`,
    null,
    adminConfig(),
  )
  return response.data
}

export async function downloadAdminDocument(documentId, signal) {
  const response = await api.get(
    `/api/documents/${encodeURIComponent(documentId)}`,
    adminConfig({ responseType: 'blob', signal }),
  )
  return response.data
}
