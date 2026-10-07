import { useEffect, useRef, useState } from 'react'
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Checkbox,
  CircularProgress,
  Container,
  FormControl,
  FormControlLabel,
  FormLabel,
  LinearProgress,
  MenuItem,
  Radio,
  RadioGroup,
  Stack,
  TextField,
  Typography,
} from '@mui/material'
import { AnimatePresence, motion } from 'framer-motion'
import { Link as RouterLink, useSearchParams } from 'react-router-dom'
import StatusBadge from '../components/StatusBadge'
import { getApiErrorMessage } from '../services/api'
import { createPrintOrder, estimatePrintOrder, getPrintOrderStatus, uploadDocument } from '../services/customerApi'
import { logUiError } from '../services/logger'

const acceptedExtensions = ['.pdf', '.docx', '.jpg', '.jpeg', '.png']
const maxFileSize = 25 * 1024 * 1024
const initialSettings = {
  printType: 'BLACK_AND_WHITE',
  copies: 1,
  paperSize: 'A4',
  orientation: 'portrait',
  doubleSided: false,
}

function isSupportedFile(file) {
  const fileName = file.name.toLowerCase()
  return acceptedExtensions.some((extension) => fileName.endsWith(extension))
}

function money(amount, currency = 'INR') {
  return new Intl.NumberFormat('en-IN', {
    style: 'currency',
    currency,
    maximumFractionDigits: 2,
  }).format(amount)
}

function CustomerPage() {
  const fileInputRef = useRef(null)
  const [document, setDocument] = useState(null)
  const [settings, setSettings] = useState(initialSettings)
  const [quote, setQuote] = useState(null)
  const [uploadProgress, setUploadProgress] = useState(0)
  const [isUploading, setIsUploading] = useState(false)
  const [isQuoting, setIsQuoting] = useState(false)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [order, setOrder] = useState(null)
  const [error, setError] = useState('')
  const [dragActive, setDragActive] = useState(false)
  const [copied, setCopied] = useState(false)

  useEffect(() => {
    if (!document || order) return undefined

    const controller = new AbortController()
    const timeout = window.setTimeout(async () => {
      setIsQuoting(true)
      try {
        const nextQuote = await estimatePrintOrder({
          documentId: document.documentId,
          printType: settings.printType,
          copies: settings.copies,
        }, controller.signal)
        setQuote(nextQuote)
        setError('')
      } catch (requestError) {
        if (requestError.code !== 'ERR_CANCELED') {
          logUiError('price_estimate_failed', requestError)
          setError(getApiErrorMessage(requestError, 'We could not calculate the price. Please try again.').message)
        }
      } finally {
        if (!controller.signal.aborted) setIsQuoting(false)
      }
    }, 250)

    return () => {
      window.clearTimeout(timeout)
      controller.abort()
    }
  }, [document, order, settings.copies, settings.printType])

  async function handleFile(file) {
    if (!file) return
    setError('')
    if (!isSupportedFile(file)) {
      setError('Choose a PDF, DOCX, JPG, or PNG file.')
      return
    }
    if (file.size > maxFileSize) {
      setError('This file is larger than the 25 MB upload limit.')
      return
    }

    setIsUploading(true)
    setUploadProgress(0)
    setDocument(null)
    setQuote(null)
    setIsQuoting(true)
    setOrder(null)
    try {
      const uploadedDocument = await uploadDocument(file, setUploadProgress)
      setDocument(uploadedDocument)
      setSettings(initialSettings)
    } catch (uploadError) {
      logUiError('document_upload_failed', uploadError)
      setError(getApiErrorMessage(uploadError, 'Upload failed. Check your connection and try again.').message)
    } finally {
      setIsUploading(false)
    }
  }

  async function handleSubmitOrder() {
    if (!document || !quote || isQuoting) return
    setIsSubmitting(true)
    setError('')
    try {
      const createdOrder = await createPrintOrder({
        documentId: document.documentId,
        printType: settings.printType,
        copies: settings.copies,
        paperSize: settings.paperSize,
        orientation: settings.orientation,
        doubleSided: settings.doubleSided,
      })
      setOrder(createdOrder)
    } catch (orderError) {
      logUiError('order_submit_failed', orderError)
      setError(getApiErrorMessage(orderError, 'We could not submit your print request. Please try again.').message)
    } finally {
      setIsSubmitting(false)
    }
  }

  async function handleCopyToken() {
    try {
      await navigator.clipboard.writeText(order.token)
      setCopied(true)
    } catch (copyError) {
      logUiError('token_copy_failed', copyError)
      setError('Clipboard access is unavailable. Select and copy the token instead.')
    }
  }

  function startOver() {
    setDocument(null)
    setQuote(null)
    setOrder(null)
    setSettings(initialSettings)
    setError('')
    setUploadProgress(0)
    setCopied(false)
    if (fileInputRef.current) fileInputRef.current.value = ''
  }

  return (
    <Container className="page-container" maxWidth="lg">
      <Box className="hero-section">
        <Typography className="eyebrow" variant="overline">PRINT MADE SIMPLE</Typography>
        <Typography component="h1" variant="h1" className="hero-title">
          Print your documents,<br /><span>without the wait.</span>
        </Typography>
        <Typography className="hero-description" color="text.secondary">
          Upload a file, choose how you want it printed, and get a token to track your order.
        </Typography>
        <div className="hero-meta">
          <span>PDF, DOCX, JPG, PNG</span><span aria-hidden="true">·</span><span>Up to 25 MB</span><span aria-hidden="true">·</span><span>Secure upload</span>
        </div>
      </Box>

      {error && <Alert className="page-alert" severity="error" onClose={() => setError('')}>{error}</Alert>}

      {order ? (
        <Card className="success-card" variant="outlined">
          <CardContent className="success-content">
            <Box className="success-icon" aria-hidden="true">✓</Box>
            <Typography component="h2" variant="h4">Your print request is in.</Typography>
            <Typography color="text.secondary">Keep your token handy to check the order status.</Typography>
            <Box className="token-display" aria-label={`Order token ${order.token}`}>{order.token}</Box>
            <StatusBadge status={order.status} />
            <Box className="success-summary">
              <SummaryLine label="Document" value={order.fileName} />
              <SummaryLine label="Pages" value={`${order.pageCount} × ${order.copies} copies`} />
              <SummaryLine label="Total" value={money(order.totalAmount, order.currency)} />
            </Box>
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5}>
              <Button onClick={handleCopyToken} variant="outlined">{copied ? 'Token copied' : 'Copy token'}</Button>
              <Button component={RouterLink} to={`/track?token=${encodeURIComponent(order.token)}`} variant="contained">Track order</Button>
              <Button onClick={startOver} color="inherit">Print another document</Button>
            </Stack>
          </CardContent>
        </Card>
      ) : (
        <Box className="workflow-grid">
          <Box className="workflow-main">
            <Card variant="outlined" className="upload-card">
              <CardContent>
                <Box className="section-heading">
                  <Box className="step-number">1</Box>
                  <Box>
                    <Typography component="h2" variant="h6">Upload your document</Typography>
                    <Typography variant="body2" color="text.secondary">Your file is checked and stored securely.</Typography>
                  </Box>
                </Box>

                {!document && (
                  <Box
                    aria-label="Document upload"
                    className={`drop-zone ${dragActive ? 'drop-zone-active' : ''} ${isUploading ? 'drop-zone-disabled' : ''}`}
                    role="region"
                    onDragEnter={(event) => { event.preventDefault(); setDragActive(true) }}
                    onDragOver={(event) => event.preventDefault()}
                    onDragLeave={(event) => { event.preventDefault(); setDragActive(false) }}
                    onDrop={(event) => {
                      event.preventDefault()
                      setDragActive(false)
                      handleFile(event.dataTransfer.files[0])
                    }}
                  >
                    <Box className="upload-icon" aria-hidden="true">↑</Box>
                    <Typography component="h3" variant="subtitle1">
                      {isUploading ? 'Uploading your file' : 'Drag and drop a file here'}
                    </Typography>
                    <Typography variant="body2" color="text.secondary">or browse from your device</Typography>
                    <input
                      ref={fileInputRef}
                      accept=".pdf,.docx,.jpg,.jpeg,.png"
                      aria-hidden="true"
                      className="visually-hidden"
                      hidden
                      id="document-file"
                      onChange={(event) => {
                        const file = event.target.files?.[0]
                        event.target.value = ''
                        handleFile(file)
                      }}
                      tabIndex={-1}
                      type="file"
                    />
                    <Button
                      disabled={isUploading}
                      onClick={() => fileInputRef.current?.click()}
                      variant="outlined"
                    >
                      Browse files
                    </Button>
                    <Typography className="upload-hint" variant="caption">
                      PDF, DOCX, JPG, or PNG · maximum 25 MB
                    </Typography>
                    {isUploading && (
                      <Box className="upload-progress">
                        <LinearProgress variant="determinate" value={uploadProgress} aria-label="Upload progress" />
                        <Typography variant="caption">Uploading… {uploadProgress}%</Typography>
                      </Box>
                    )}
                  </Box>
                )}

                {document && (
                  <Box className="file-ready">
                    <Box className="file-symbol" aria-hidden="true">▤</Box>
                    <Box className="file-details">
                      <Typography className="file-name" title={document.fileName}>{document.fileName}</Typography>
                      <Typography variant="body2" color="text.secondary">
                        {document.pageCount ?? 'Page count unavailable'} pages · {formatBytes(document.sizeBytes)}
                      </Typography>
                    </Box>
                    <Button aria-label="Remove uploaded file" onClick={startOver} size="small">Remove</Button>
                  </Box>
                )}
              </CardContent>
            </Card>

            <AnimatePresence initial={false}>
              {document && (
                <motion.div
                  animate={{ opacity: 1, y: 0 }}
                  exit={{ opacity: 0, y: 8 }}
                  initial={{ opacity: 0, y: 8 }}
                  transition={{ duration: 0.2 }}
                >
                  <Card className="settings-card" variant="outlined">
                    <CardContent>
                      <Box className="section-heading">
                        <Box className="step-number">2</Box>
                        <Box>
                          <Typography component="h2" variant="h6">Choose print settings</Typography>
                          <Typography variant="body2" color="text.secondary">You can review the price before submitting.</Typography>
                        </Box>
                      </Box>

                      <Stack className="settings-fields" spacing={3}>
                        <FormControl>
                          <FormLabel id="print-type-label">Print type</FormLabel>
                          <RadioGroup
                            aria-labelledby="print-type-label"
                            className="print-type-options"
                            onChange={(event) => updateSetting('printType', event.target.value)}
                            value={settings.printType}
                          >
                            <FormControlLabel control={<Radio />} label="Black & white" value="BLACK_AND_WHITE" />
                            <FormControlLabel control={<Radio />} label="Color" value="COLOR" />
                          </RadioGroup>
                        </FormControl>

                        <Box className="settings-row">
                          <TextField
                            fullWidth
                            slotProps={{ htmlInput: { min: 1, max: 999 } }}
                            label="Copies"
                            onChange={(event) => updateSetting('copies', Math.max(1, Math.min(999, Number(event.target.value) || 1)))}
                            type="number"
                            value={settings.copies}
                          />
                          <TextField
                            fullWidth
                            label="Paper size"
                            onChange={(event) => updateSetting('paperSize', event.target.value)}
                            select
                            value={settings.paperSize}
                          >
                            {['A4', 'A3', 'Letter', 'Legal'].map((size) => <MenuItem key={size} value={size}>{size}</MenuItem>)}
                          </TextField>
                        </Box>

                        <FormControl>
                          <FormLabel id="orientation-label">Orientation</FormLabel>
                          <RadioGroup
                            aria-labelledby="orientation-label"
                            className="orientation-options"
                            onChange={(event) => updateSetting('orientation', event.target.value)}
                            row
                            value={settings.orientation}
                          >
                            <FormControlLabel control={<Radio />} label="Portrait" value="portrait" />
                            <FormControlLabel control={<Radio />} label="Landscape" value="landscape" />
                          </RadioGroup>
                        </FormControl>

                        <FormControlLabel
                          control={<Checkbox checked={settings.doubleSided} onChange={(event) => updateSetting('doubleSided', event.target.checked)} />}
                          label="Print double-sided"
                        />
                      </Stack>
                    </CardContent>
                  </Card>
                </motion.div>
              )}
            </AnimatePresence>
          </Box>

          <Box className="summary-column">
            <Card className="summary-card" variant="outlined">
              <CardContent>
                <Box className="section-heading summary-heading">
                  <Box className="step-number">3</Box>
                  <Box>
                    <Typography component="h2" variant="h6">Print summary</Typography>
                    <Typography variant="body2" color="text.secondary">Price from the current print rate.</Typography>
                  </Box>
                </Box>

                {document ? (
                  <>
                    <Stack spacing={1.5} className="summary-lines">
                      <SummaryLine label="Document" value={document.fileName} />
                      <SummaryLine label="Pages" value={quote?.documentPages ?? document.pageCount ?? '—'} />
                      <SummaryLine label="Print type" value={settings.printType === 'COLOR' ? 'Color' : 'Black & white'} />
                      <SummaryLine label="Copies" value={settings.copies} />
                      <SummaryLine label="Total pages" value={quote?.totalPages ?? '—'} />
                      {quote && <SummaryLine label="Price per page" value={money(quote.pricePerPage, quote.currency)} />}
                    </Stack>
                    <Box className="summary-total">
                      <Typography color="text.secondary" variant="body2">Estimated total</Typography>
                      <Typography component="p" className="total-amount" variant="h4">
                        {isQuoting || !quote ? <CircularProgress size={25} aria-label="Calculating price" /> : money(quote.totalAmount, quote.currency)}
                      </Typography>
                    </Box>
                    <Button
                      className="submit-order-button"
                      disabled={!quote || isQuoting || isSubmitting}
                      fullWidth
                      onClick={handleSubmitOrder}
                      variant="contained"
                    >
                      {isSubmitting ? <CircularProgress color="inherit" size={20} /> : 'Submit print request'}
                    </Button>
                    <Typography className="price-note" color="text.secondary" variant="caption">
                      Final price is confirmed by the server when your request is submitted.
                    </Typography>
                  </>
                ) : (
                  <Box className="summary-empty">
                    <Box className="summary-empty-icon" aria-hidden="true">▤</Box>
                    <Typography color="text.secondary" variant="body2">
                      Upload a document to see your page count and estimated price.
                    </Typography>
                  </Box>
                )}
              </CardContent>
            </Card>
            <Box className="privacy-note">
              <span aria-hidden="true">⌑</span>
              <Typography color="text.secondary" variant="caption">Your document is stored privately and only shared with authorized print staff.</Typography>
            </Box>
          </Box>
        </Box>
      )}

      <TrackOrderCard />
    </Container>
  )

  function updateSetting(key, value) {
    setQuote(null)
    setIsQuoting(true)
    setSettings((current) => ({ ...current, [key]: value }))
  }
}

function SummaryLine({ label, value }) {
  return (
    <Box className="summary-line">
      <Typography color="text.secondary" variant="body2">{label}</Typography>
      <Typography className="summary-value" variant="body2">{value}</Typography>
    </Box>
  )
}

export function TrackOrderCard() {
  const [searchParams] = useSearchParams()
  const [token, setToken] = useState(searchParams.get('token')?.toUpperCase() || '')
  const [result, setResult] = useState(null)
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  async function handleLookup(event) {
    event.preventDefault()
    const normalizedToken = token.trim().toUpperCase()
    if (!normalizedToken) {
      setError('Enter the order token to check its status.')
      return
    }
    setLoading(true)
    setError('')
    setResult(null)
    try {
      setResult(await getPrintOrderStatus(normalizedToken))
    } catch (lookupError) {
      logUiError('order_status_lookup_failed', lookupError)
      setError(getApiErrorMessage(lookupError, 'We could not find that order. Check the token and try again.').message)
    } finally {
      setLoading(false)
    }
  }

  return (
    <Card className="track-card" variant="outlined">
      <CardContent>
        <Box className="track-card-heading">
          <Box>
            <Typography component="h2" variant="h6">Already have a token?</Typography>
            <Typography color="text.secondary" variant="body2">Enter it here to check your order status.</Typography>
          </Box>
        </Box>
        <Box className="track-form" component="form" onSubmit={handleLookup}>
          <TextField
            autoCapitalize="characters"
            fullWidth
            label="Order token"
            slotProps={{ htmlInput: { maxLength: 12 } }}
            onChange={(event) => setToken(event.target.value.toUpperCase())}
            placeholder="e.g. 7KX3M9P2QW6A"
            value={token}
          />
          <Button disabled={loading} type="submit" variant="outlined">
            {loading ? <CircularProgress size={20} /> : 'Check status'}
          </Button>
        </Box>
        {error && <Alert className="track-result" severity="error">{error}</Alert>}
        {result && (
          <Box className="track-result" role="status">
            <Box className="track-result-heading">
              <Typography className="token-result">{result.token}</Typography>
              <StatusBadge status={result.status} />
            </Box>
            <Typography color="text.secondary" variant="body2">
              {result.pageCount} pages · {result.copies} copies · {money(result.totalAmount)}
            </Typography>
          </Box>
        )}
      </CardContent>
    </Card>
  )
}

function formatBytes(bytes) {
  if (!Number.isFinite(bytes) || bytes < 0) return 'Size unavailable'
  if (bytes < 1024 * 1024) return `${Math.max(1, Math.round(bytes / 1024))} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

export default CustomerPage
