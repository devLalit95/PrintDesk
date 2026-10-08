import { useEffect, useState } from 'react'
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControl,
  InputLabel,
  MenuItem,
  Select,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableHead as MuiTableHead,
  TableContainer,
  TablePagination,
  TableRow,
  TextField,
  Typography,
} from '@mui/material'
import { Navigate } from 'react-router-dom'
import StatusBadge from '../components/StatusBadge'
import { getApiErrorMessage } from '../services/api'
import {
  cancelAdminOrder,
  downloadAdminDocument,
  getAdminDashboardStats,
  getAdminOrder,
  getAdminOrders,
} from '../services/adminApi'
import { logUiError } from '../services/logger'
import useAuthStore from '../store/authStore'

const pageSizeOptions = [10, 20, 50]
const orderStatuses = [
  'PENDING',
  'PRINT_REQUESTED',
  'QUEUED',
  'PRINTING',
  'PRINTED',
  'FAILED',
  'OUTCOME_UNKNOWN',
  'CANCELLED',
]

function AdminPage() {
  const accessToken = useAuthStore((state) => state.accessToken)
  const expiresAt = useAuthStore((state) => state.expiresAt)
  const clearSession = useAuthStore((state) => state.clearSession)
  const [stats, setStats] = useState(null)
  const [orders, setOrders] = useState(null)
  const [page, setPage] = useState(0)
  const [pageSize, setPageSize] = useState(10)
  const [status, setStatus] = useState('')
  const [searchInput, setSearchInput] = useState('')
  const [search, setSearch] = useState('')
  const [refreshKey, setRefreshKey] = useState(0)
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [selectedOrderId, setSelectedOrderId] = useState(null)
  const [selectedOrder, setSelectedOrder] = useState(null)
  const [detailsLoading, setDetailsLoading] = useState(false)
  const [detailsError, setDetailsError] = useState('')
  const [cancelConfirmation, setCancelConfirmation] = useState(false)
  const [actionLoading, setActionLoading] = useState(false)

  useEffect(() => {
    if (!accessToken) return undefined

    const controller = new AbortController()
    Promise.all([
      getAdminOrders({
        page,
        size: pageSize,
        status: status || undefined,
        search: search || undefined,
      }, controller.signal),
      getAdminDashboardStats(controller.signal),
    ])
      .then(([orderPage, nextStats]) => {
        setOrders(orderPage)
        setStats(nextStats)
      })
      .catch((requestError) => {
        if (requestError.code !== 'ERR_CANCELED') {
          logUiError('admin_dashboard_load_failed', requestError)
          setError(getApiErrorMessage(requestError, 'The dashboard could not be loaded. Try again.').message)
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setIsLoading(false)
      })

    return () => controller.abort()
  }, [accessToken, page, pageSize, refreshKey, search, status])

  useEffect(() => {
    if (!selectedOrderId || !accessToken) return undefined

    const controller = new AbortController()
    getAdminOrder(selectedOrderId, controller.signal)
      .then(setSelectedOrder)
      .catch((requestError) => {
        if (requestError.code !== 'ERR_CANCELED') {
          logUiError('admin_order_details_failed', requestError)
          setDetailsError(getApiErrorMessage(requestError, 'Order details could not be loaded.').message)
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setDetailsLoading(false)
      })

    return () => controller.abort()
  }, [accessToken, selectedOrderId])

  if (!accessToken || !expiresAt) {
    return <Navigate replace to="/admin/login" />
  }

  function applySearch(event) {
    event.preventDefault()
    if (search !== searchInput.trim()) {
      setIsLoading(true)
      setError('')
    }
    setPage(0)
    setSearch(searchInput.trim())
  }

  function clearFilters() {
    if (search || searchInput || status || page !== 0) {
      setIsLoading(true)
      setError('')
    }
    setSearchInput('')
    setSearch('')
    setStatus('')
    setPage(0)
  }

  function openOrderDetails(orderId) {
    setSelectedOrder(null)
    setDetailsError('')
    setDetailsLoading(true)
    setSelectedOrderId(orderId)
  }

  function closeOrderDetails() {
    if (actionLoading) return
    setSelectedOrderId(null)
    setSelectedOrder(null)
    setCancelConfirmation(false)
  }

  async function handleCancelOrder() {
    if (!selectedOrder) return
    setActionLoading(true)
    setDetailsError('')
    try {
      const updatedOrder = await cancelAdminOrder(selectedOrder.id)
      setSelectedOrder(updatedOrder)
      setCancelConfirmation(false)
      setNotice(`Order ${updatedOrder.token} was cancelled.`)
      setIsLoading(true)
      setRefreshKey((current) => current + 1)
    } catch (requestError) {
      logUiError('admin_order_cancel_failed', requestError)
      setDetailsError(getApiErrorMessage(requestError, 'The order could not be cancelled.').message)
    } finally {
      setActionLoading(false)
    }
  }

  async function handleDownload() {
    if (!selectedOrder) return
    try {
      const blob = await downloadAdminDocument(selectedOrder.documentId)
      const objectUrl = URL.createObjectURL(blob)
      const link = window.document.createElement('a')
      link.href = objectUrl
      link.download = selectedOrder.fileName
      link.click()
      window.setTimeout(() => URL.revokeObjectURL(objectUrl), 0)
    } catch (requestError) {
      logUiError('admin_document_download_failed', requestError)
      setDetailsError(getApiErrorMessage(requestError, 'The document could not be downloaded.').message)
    }
  }

  return (
    <Box className="admin-page">
      <Box className="admin-heading">
        <Box>
          <Typography className="eyebrow" variant="overline">PRINTDESK ADMIN</Typography>
          <Typography component="h1" variant="h3">Order dashboard</Typography>
          <Typography color="text.secondary">
            Review customer print requests and their current status.
          </Typography>
        </Box>
        <Stack direction="row" spacing={1}>
          <Button
            onClick={() => {
              setIsLoading(true)
              setError('')
              setRefreshKey((current) => current + 1)
            }}
            disabled={isLoading}
            variant="outlined"
          >
            Refresh
          </Button>
          <Button onClick={clearSession} variant="text">Sign out</Button>
        </Stack>
      </Box>

      <Alert className="admin-api-notice" severity="info">
        This dashboard does not yet expose print or retry controls. Queue and agent APIs are available, but the local agent transport and authenticated notification loop are not integrated.
      </Alert>
      {notice && <Alert className="admin-notice-message" onClose={() => setNotice('')} severity="success">{notice}</Alert>}
      {error && <Alert className="admin-notice-message" onClose={() => setError('')} severity="error">{error}</Alert>}

      <Box className="admin-stats-grid">
        <StatCard label="All orders" value={stats?.totalOrders} />
        <StatCard label="Waiting for review" value={stats?.pendingOrders} />
        <StatCard label="In progress" value={stats?.inProgressOrders} />
        <StatCard label="Printed" value={stats?.printedOrders} />
        <StatCard label="Failed" value={stats?.failedOrders} />
        <StatCard label="Needs outcome review" value={stats?.outcomeUnknownOrders} />
        <StatCard label="Cancelled" value={stats?.cancelledOrders} />
      </Box>

      <Card className="admin-orders-card" variant="outlined">
        <CardContent>
          <Box className="admin-orders-heading">
            <Box>
              <Typography component="h2" variant="h5">Print requests</Typography>
              <Typography color="text.secondary" variant="body2">
                Search by token or document filename.
              </Typography>
            </Box>
            {isLoading && <CircularProgress aria-label="Loading orders" size={24} />}
          </Box>

          <Box className="admin-filters" component="form" onSubmit={applySearch}>
            <TextField
              fullWidth
              label="Search orders"
              onChange={(event) => setSearchInput(event.target.value)}
              slotProps={{ htmlInput: { maxLength: 100 } }}
              value={searchInput}
            />
            <FormControl fullWidth>
              <InputLabel id="order-status-filter-label">Status</InputLabel>
              <Select
                label="Status"
                labelId="order-status-filter-label"
                onChange={(event) => {
                  if (event.target.value !== status) {
                    setIsLoading(true)
                    setError('')
                  }
                  setPage(0)
                  setStatus(event.target.value)
                }}
                value={status}
              >
                <MenuItem value="">All statuses</MenuItem>
                {orderStatuses.map((orderStatus) => (
                  <MenuItem key={orderStatus} value={orderStatus}>
                    {orderStatus.replaceAll('_', ' ')}
                  </MenuItem>
                ))}
              </Select>
            </FormControl>
            <Button type="submit" variant="contained">Search</Button>
            <Button onClick={clearFilters} type="button" variant="text">Clear</Button>
          </Box>

          {isLoading && !orders ? (
            <Box className="admin-loading"><CircularProgress /><Typography>Loading print requests…</Typography></Box>
          ) : !orders && error ? (
            <Box className="admin-empty-state">
              <Typography component="h3" variant="h6">Dashboard data unavailable</Typography>
              <Typography color="text.secondary">Refresh the page to try loading the dashboard again.</Typography>
            </Box>
          ) : orders?.items.length ? (
            <>
              <TableContainer className="admin-table-scroll">
                <Table aria-label="Print requests">
                  <MuiTableHead>
                    <TableHead />
                  </MuiTableHead>
                  <TableBody>
                    {orders.items.map((order) => (
                      <TableRow hover key={order.id}>
                        <TableCell>
                          <Typography className="order-token" variant="body2">{order.token}</Typography>
                          <Typography className="order-file-name" variant="body2" title={order.fileName}>
                            {order.fileName}
                          </Typography>
                          <Typography color="text.secondary" variant="caption">
                            {new Date(order.createdAt).toLocaleString()}
                          </Typography>
                        </TableCell>
                        <TableCell>
                          <StatusBadge status={order.status} />
                        </TableCell>
                        <TableCell>
                          {order.printType === 'COLOR' ? 'Color' : 'Black & white'}
                          <Typography display="block" color="text.secondary" variant="caption">
                            {order.pageCount} pages × {order.copies} copies
                          </Typography>
                        </TableCell>
                        <TableCell>{formatMoney(order.totalAmount, order.currency)}</TableCell>
                        <TableCell align="right">
                          <Button onClick={() => openOrderDetails(order.id)} size="small" variant="outlined">
                            Details
                          </Button>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
              <TablePagination
                component="div"
                count={orders.totalItems}
                onPageChange={(_event, nextPage) => {
                  setIsLoading(true)
                  setError('')
                  setPage(nextPage)
                }}
                onRowsPerPageChange={(event) => {
                  setIsLoading(true)
                  setError('')
                  setPage(0)
                  setPageSize(Number(event.target.value))
                }}
                page={orders.page}
                rowsPerPage={orders.size}
                rowsPerPageOptions={pageSizeOptions}
              />
            </>
          ) : (
            <Box className="admin-empty-state">
              <Typography component="h3" variant="h6">No print requests found</Typography>
              <Typography color="text.secondary">
                Try another status or search term. New customer orders will appear here.
              </Typography>
            </Box>
          )}
        </CardContent>
      </Card>

      <Dialog
        fullWidth
        maxWidth="sm"
        onClose={() => {
          closeOrderDetails()
        }}
        open={Boolean(selectedOrderId)}
      >
        <DialogTitle>Print request details</DialogTitle>
        <DialogContent dividers>
          {detailsError && <Alert className="detail-alert" severity="error">{detailsError}</Alert>}
          {detailsLoading ? (
            <Box className="admin-loading"><CircularProgress /><Typography>Loading order details…</Typography></Box>
          ) : selectedOrder ? (
            <>
              <Box className="detail-status-row">
                <StatusBadge status={selectedOrder.status} />
                <Typography className="order-token" variant="subtitle1">{selectedOrder.token}</Typography>
              </Box>
              <Box className="order-detail-grid">
                <DetailValue label="Document" value={selectedOrder.fileName} />
                <DetailValue label="Pages" value={`${selectedOrder.pageCount} pages × ${selectedOrder.copies} copies`} />
                <DetailValue label="Print type" value={selectedOrder.printType === 'COLOR' ? 'Color' : 'Black & white'} />
                <DetailValue label="Paper" value={`${selectedOrder.paperSize}, ${selectedOrder.orientation}`} />
                <DetailValue label="Sides" value={selectedOrder.doubleSided ? 'Double-sided' : 'Single-sided'} />
                <DetailValue label="Total" value={formatMoney(selectedOrder.totalAmount, selectedOrder.currency)} />
                <DetailValue label="Created" value={new Date(selectedOrder.createdAt).toLocaleString()} />
                {selectedOrder.printedAt && <DetailValue label="Printed" value={new Date(selectedOrder.printedAt).toLocaleString()} />}
              </Box>
              <Button className="download-document-button" onClick={handleDownload} variant="outlined">
                Download document
              </Button>
              <Typography className="attempt-heading" component="h3" variant="subtitle1">Print history</Typography>
              {selectedOrder.attempts.length ? (
                <Stack spacing={1}>
                  {selectedOrder.attempts.map((attempt) => (
                    <Box className="attempt-item" key={attempt.id}>
                      <Typography variant="body2">Attempt {attempt.attemptNumber}</Typography>
                      <StatusBadge status={attempt.status} />
                      {attempt.errorMessage && (
                        <Typography color="error" variant="caption">{attempt.errorMessage}</Typography>
                      )}
                    </Box>
                  ))}
                </Stack>
              ) : (
                <Typography color="text.secondary" variant="body2">No print attempts have been recorded.</Typography>
              )}
              {selectedOrder.status === 'PENDING' && (
                <Alert className="cancel-order-alert" severity="warning">
                  Cancelling is permanent. Print and retry actions remain unavailable until agent integration is complete.
                </Alert>
              )}
            </>
          ) : !detailsError ? (
            <Typography color="text.secondary">Order details are unavailable.</Typography>
          ) : null}
        </DialogContent>
        <DialogActions>
          <Button
            onClick={closeOrderDetails}
            disabled={actionLoading}
          >
            Close
          </Button>
          {selectedOrder?.status === 'PENDING' && (
            <Button
              color="error"
              disabled={detailsLoading || actionLoading}
              onClick={() => setCancelConfirmation(true)}
              variant="contained"
            >
              Cancel order
            </Button>
          )}
        </DialogActions>
      </Dialog>

      <Dialog
        onClose={() => !actionLoading && setCancelConfirmation(false)}
        open={cancelConfirmation}
      >
        <DialogTitle>Cancel this print request?</DialogTitle>
        <DialogContent>
          <Typography color="text.secondary">
            This will permanently cancel token {selectedOrder?.token}. This action cannot be undone.
          </Typography>
        </DialogContent>
        <DialogActions>
          <Button disabled={actionLoading} onClick={() => setCancelConfirmation(false)}>Keep order</Button>
          <Button
            color="error"
            disabled={actionLoading}
            onClick={handleCancelOrder}
            variant="contained"
          >
            {actionLoading ? <CircularProgress color="inherit" size={18} /> : 'Confirm cancellation'}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  )
}

function StatCard({ label, value }) {
  return (
    <Card className="admin-stat-card" variant="outlined">
      <CardContent>
        <Typography color="text.secondary" variant="body2">{label}</Typography>
        {value === undefined ? (
          <CircularProgress className="stat-loading" size={23} />
        ) : (
          <Typography component="p" variant="h4">{value.toLocaleString()}</Typography>
        )}
      </CardContent>
    </Card>
  )
}

function DetailValue({ label, value }) {
  return (
    <Box className="detail-value">
      <Typography color="text.secondary" variant="caption">{label}</Typography>
      <Typography variant="body2">{value}</Typography>
    </Box>
  )
}

function TableHead() {
  return (
    <TableRow>
      <TableCell>Order</TableCell>
      <TableCell>Status</TableCell>
      <TableCell>Print settings</TableCell>
      <TableCell>Total</TableCell>
      <TableCell align="right">Actions</TableCell>
    </TableRow>
  )
}

function formatMoney(amount, currency = 'INR') {
  return new Intl.NumberFormat('en-IN', {
    style: 'currency',
    currency,
    maximumFractionDigits: 2,
  }).format(amount)
}

export default AdminPage
