import { Box, Container, Typography } from '@mui/material'
import { TrackOrderCard } from './CustomerPage'

function TrackPage() {
  return (
    <Container className="page-container track-page" maxWidth="md">
      <Box className="track-page-intro">
        <Typography className="eyebrow" variant="overline">ORDER STATUS</Typography>
        <Typography component="h1" variant="h2">Track your print</Typography>
        <Typography color="text.secondary">
          Enter the token from your confirmation to see the latest status.
        </Typography>
      </Box>
      <TrackOrderCard />
    </Container>
  )
}

export default TrackPage
