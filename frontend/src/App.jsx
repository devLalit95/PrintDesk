import { Box } from '@mui/material'
import { Route, Routes, useLocation } from 'react-router-dom'
import AppHeader from './components/AppHeader'
import AdminLoginPage from './pages/AdminLoginPage'
import AdminPage from './pages/AdminPage'
import CustomerPage from './pages/CustomerPage'
import TrackPage from './pages/TrackPage'

function App() {
  const location = useLocation()

  return (
    <Box className="app-shell">
      <AppHeader />
      <Routes location={location}>
        <Route element={<CustomerPage />} path="/" />
        <Route element={<TrackPage />} path="/track" />
        <Route element={<AdminLoginPage />} path="/admin/login" />
        <Route element={<AdminPage />} path="/admin" />
        <Route element={<CustomerPage />} path="*" />
      </Routes>
      <footer className="app-footer">
        <span>PrintDesk</span>
        <span>Fast. Simple. Ready to print.</span>
      </footer>
    </Box>
  )
}

export default App
