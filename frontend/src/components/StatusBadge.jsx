import { Chip } from '@mui/material'

const statusColors = {
  PENDING: 'warning',
  PRINT_REQUESTED: 'info',
  QUEUED: 'info',
  PRINTING: 'primary',
  PRINTED: 'success',
  FAILED: 'error',
  CANCELLED: 'default',
}

function StatusBadge({ status }) {
  return (
    <Chip
      color={statusColors[status] || 'default'}
      label={status?.replaceAll('_', ' ') || 'Unknown'}
      size="small"
      variant="outlined"
    />
  )
}

export default StatusBadge
