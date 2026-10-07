import { AppBar, Box, Button, Container, Toolbar, Typography } from '@mui/material'
import { Link as RouterLink } from 'react-router-dom'

function AppHeader() {
  return (
    <AppBar className="app-header" position="sticky" color="inherit" elevation={0}>
      <Container maxWidth="lg">
        <Toolbar disableGutters className="header-toolbar">
          <RouterLink aria-label="PrintDesk home" className="brand-link" to="/">
            <Box className="brand-mark" aria-hidden="true">P</Box>
            <Typography component="span" className="brand-name">printdesk</Typography>
          </RouterLink>
          <Box className="header-links">
            <Button component={RouterLink} color="inherit" to="/track">Track order</Button>
            <Button component={RouterLink} variant="outlined" to="/admin/login">Admin</Button>
          </Box>
        </Toolbar>
      </Container>
    </AppBar>
  )
}

export default AppHeader
