import { useState } from 'react'
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  CircularProgress,
  TextField,
  Typography,
} from '@mui/material'
import { Navigate, useNavigate } from 'react-router-dom'
import { getApiErrorMessage } from '../services/api'
import { loginAdmin } from '../services/adminApi'
import { logUiError } from '../services/logger'
import useAuthStore from '../store/authStore'

function AdminLoginPage() {
  const navigate = useNavigate()
  const accessToken = useAuthStore((state) => state.accessToken)
  const setSession = useAuthStore((state) => state.setSession)
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [isSubmitting, setIsSubmitting] = useState(false)

  if (accessToken) {
    return <Navigate replace to="/admin" />
  }

  async function handleSubmit(event) {
    event.preventDefault()
    setError('')
    setIsSubmitting(true)
    try {
      const session = await loginAdmin({ username: username.trim(), password })
      setSession(session)
      setPassword('')
      navigate('/admin', { replace: true })
    } catch (loginError) {
      logUiError('admin_login_failed', loginError)
      setError(getApiErrorMessage(loginError, 'Sign-in failed. Check your connection and try again.').message)
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <Box className="auth-page">
      <Card className="auth-card" variant="outlined">
        <CardContent>
          <Box className="auth-mark" aria-hidden="true">P</Box>
          <Typography className="eyebrow" variant="overline">PRINTDESK ADMIN</Typography>
          <Typography component="h1" variant="h4">Welcome back</Typography>
          <Typography className="auth-description" color="text.secondary">
            Sign in to access administrator tools.
          </Typography>
          {error && <Alert className="auth-alert" severity="error">{error}</Alert>}
          <Box component="form" onSubmit={handleSubmit}>
            <TextField
              autoComplete="username"
              fullWidth
              label="Username"
              margin="normal"
              onChange={(event) => setUsername(event.target.value)}
              required
              value={username}
            />
            <TextField
              autoComplete="current-password"
              fullWidth
              id="admin-password"
              label="Password"
              margin="normal"
              onChange={(event) => setPassword(event.target.value)}
              required
              type="password"
              value={password}
            />
            <Button
              className="auth-submit"
              disabled={isSubmitting}
              fullWidth
              type="submit"
              variant="contained"
            >
              {isSubmitting ? <CircularProgress color="inherit" size={20} /> : 'Sign in'}
            </Button>
          </Box>
          <Typography className="auth-footnote" color="text.secondary" variant="caption">
            Your session uses a short-lived secure access token.
          </Typography>
        </CardContent>
      </Card>
    </Box>
  )
}

export default AdminLoginPage
