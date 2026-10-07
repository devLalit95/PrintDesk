import { useState } from 'react'

const uploadSteps = {
  IDLE: 'idle',
  UPLOADING: 'uploading',
  CONFIG: 'config',
  SUMMARY: 'summary',
  CONFIRMED: 'confirmed',
}

const dummyFile = {
  name: 'resume.pdf',
  pages: 4,
  size: '2.4 MB',
}

const pricing = {
  blackAndWhite: 2,
  color: 5,
}

function App() {
  const [step, setStep] = useState(uploadSteps.IDLE)
  const [printType, setPrintType] = useState('color')
  const [copies, setCopies] = useState(1)
  const [paperSize, setPaperSize] = useState('A4')
  const [orientation, setOrientation] = useState('portrait')
  const [uploadProgress, setUploadProgress] = useState(0)
  const [token, setToken] = useState(null)

  const pagePrice = printType === 'color' ? pricing.color : pricing.blackAndWhite
  const totalPages = dummyFile.pages * copies
  const totalAmount = totalPages * pagePrice

  const handleUpload = () => {
    setStep(uploadSteps.UPLOADING)
    let progress = 0
    const interval = setInterval(() => {
      progress += 10
      setUploadProgress(progress)
      if (progress >= 100) {
        clearInterval(interval)
        setStep(uploadSteps.CONFIG)
      }
    }, 200)
  }

  const handleOrder = () => {
    setStep(uploadSteps.SUMMARY)
  }

  const handleSubmit = () => {
    setToken(Math.floor(1000 + Math.random() * 9000))
    setStep(uploadSteps.CONFIRMED)
  }

  const reset = () => {
    setStep(uploadSteps.IDLE)
    setPrintType('color')
    setCopies(1)
    setPaperSize('A4')
    setOrientation('portrait')
    setUploadProgress(0)
    setToken(null)
  }

  return (
    <div className="min-h-screen bg-base flex flex-col items-center">
      <div className="w-full max-w-4xl mx-auto px-6 py-12 lg:py-16">
        <header className="text-center mb-12">
          <h1 className="text-5xl font-bold text-fg mb-4">
            Print Your Documents
          </h1>
          <p className="text-lg text-text-secondary max-w-2xl mx-auto">
            Fast. Simple. Ready to Print.
          </p>
        </header>

        {step === uploadSteps.IDLE && (
          <div className="card p-8 mb-8 text-center">
            <div className="mb-6">
              <div className="w-16 h-16 mx-auto rounded-full bg-primary/10 flex items-center justify-center mb-4">
                <svg
                  className="w-8 h-8 text-primary"
                  fill="none"
                  stroke="currentColor"
                  viewBox="0 0 24 24"
                >
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    strokeWidth={2}
                    d="M7 16V4h10v4m-4 4h.01M12 12v4m0 0v-4m0 4l-2-2m2 2l2-2"
                  />
                </svg>
              </div>
              <h2 className="text-2xl font-semibold text-fg mb-2">
                Upload your document
              </h2>
              <p className="text-sm text-text-secondary">
                PDF, DOCX, JPG, PNG supported
              </p>
            </div>
            <button
              type="button"
              onClick={handleUpload}
              className="btn-primary px-8 py-3 text-base mx-auto block"
            >
              Upload Document
            </button>
          </div>
        )}

        {step === uploadSteps.UPLOADING && (
          <div className="card p-8 mb-8">
            <div className="space-y-4">
              <div className="flex items-center justify-between">
                <span className="text-sm font-medium text-fg">
                  {dummyFile.name}
                </span>
                <span className="text-sm text-text-secondary">
                  {uploadProgress}%
                </span>
              </div>
              <div className="w-full bg-border rounded-full h-2 overflow-hidden">
                <div
                  className="h-full bg-primary rounded-full transition-all duration-300"
                  style={{ width: `${uploadProgress}%` }}
                />
              </div>
              <p className="text-sm text-text-secondary">
                Uploading your document...
              </p>
            </div>
          </div>
        )}

        {step === uploadSteps.CONFIG && (
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
            <div className="lg:col-span-2 space-y-6">
              <div className="card p-6">
                <h2 className="text-xl font-semibold text-fg mb-4">
                  Print Settings
                </h2>

                <div className="space-y-6">
                  <div>
                    <label className="block text-sm font-medium text-fg mb-3">
                      Print Type
                    </label>
                    <div className="flex gap-4">
                      <button
                        type="button"
                        onClick={() => setPrintType('bw')}
                        className={`flex-1 py-3 px-4 border-2 rounded-lg text-center transition-fast ${
                          printType === 'bw'
                            ? 'border-primary bg-primary/5 text-primary'
                            : 'border-border hover:border-border text-text-secondary'
                        }`}
                      >
                        <span className="font-medium">Black & White</span>
                      </button>
                      <button
                        type="button"
                        onClick={() => setPrintType('color')}
                        className={`flex-1 py-3 px-4 border-2 rounded-lg text-center transition-fast ${
                          printType === 'color'
                            ? 'border-primary bg-primary/5 text-primary'
                            : 'border-border hover:border-border text-text-secondary'
                        }`}
                      >
                        <span className="font-medium">Color</span>
                      </button>
                    </div>
                  </div>

                  <div>
                    <label className="block text-sm font-medium text-fg mb-3">
                      Copies
                    </label>
                    <div className="flex items-center gap-3">
                      <button
                        type="button"
                        onClick={() => setCopies(Math.max(1, copies - 1))}
                        className="w-10 h-10 border border-border rounded-lg flex items-center justify-center hover:bg-surface transition-fast"
                      >
                        -
                      </button>
                      <span className="text-xl font-medium w-12 text-center">
                        {copies}
                      </span>
                      <button
                        type="button"
                        onClick={() => setCopies(copies + 1)}
                        className="w-10 h-10 border border-border rounded-lg flex items-center justify-center hover:bg-surface transition-fast"
                      >
                        +
                      </button>
                    </div>
                  </div>

                  <div>
                    <label className="block text-sm font-medium text-fg mb-3">
                      Paper Size
                    </label>
                    <select
                      value={paperSize}
                      onChange={(e) => setPaperSize(e.target.value)}
                      className="w-full px-3 py-2 border border-border rounded-lg bg-surface text-fg focus:outline-none focus:ring-2 focus:ring-primary/20"
                    >
                      <option value="A4">A4</option>
                      <option value="A3">A3</option>
                      <option value="Letter">Letter</option>
                      <option value="Legal">Legal</option>
                    </select>
                  </div>

                  <div>
                    <label className="block text-sm font-medium text-fg mb-3">
                      Orientation
                    </label>
                    <div className="flex gap-4">
                      <button
                        type="button"
                        onClick={() => setOrientation('portrait')}
                        className={`flex-1 py-2 px-4 border-2 rounded-lg text-center transition-fast ${
                          orientation === 'portrait'
                            ? 'border-primary bg-primary/5 text-primary'
                            : 'border-border hover:border-border text-text-secondary'
                        }`}
                      >
                        Portrait
                      </button>
                      <button
                        type="button"
                        onClick={() => setOrientation('landscape')}
                        className={`flex-1 py-2 px-4 border-2 rounded-lg text-center transition-fast ${
                          orientation === 'landscape'
                            ? 'border-primary bg-primary/5 text-primary'
                            : 'border-border hover:border-border text-text-secondary'
                        }`}
                      >
                        Landscape
                      </button>
                    </div>
                  </div>
                </div>

                <button
                  type="button"
                  onClick={handleOrder}
                  className="btn-primary w-full mt-6 py-3 text-base"
                >
                  Continue
                </button>
              </div>
            </div>

            {step === uploadSteps.CONFIG && (
              <div>
                <div className="card p-6 sticky top-6">
                  <h3 className="text-lg font-semibold text-fg mb-4">
                    Print Summary
                  </h3>

                  <div className="space-y-3 text-sm mb-6">
                    <div className="flex justify-between">
                      <span className="text-text-secondary">Document</span>
                      <span className="text-fg font-medium">
                        {dummyFile.name}
                      </span>
                    </div>
                    <div className="flex justify-between">
                      <span className="text-text-secondary">Pages</span>
                      <span className="text-fg">{dummyFile.pages}</span>
                    </div>
                    <div className="flex justify-between">
                      <span className="text-text-secondary">Print Type</span>
                      <span className="text-fg capitalize">
                        {printType === 'bw' ? 'Black & White' : 'Color'}
                      </span>
                    </div>
                    <div className="flex justify-between">
                      <span className="text-text-secondary">Copies</span>
                      <span className="text-fg">{copies}</span>
                    </div>
                    <div className="flex justify-between">
                      <span className="text-text-secondary">
                        Price / page
                      </span>
                      <span className="text-fg">
                        ₹{pagePrice} ({
                          printType === 'bw' ? 'B&W' : 'Color'
                        })
                      </span>
                    </div>
                    <div className="flex justify-between">
                      <span className="text-text-secondary">
                        Total pages
                      </span>
                      <span className="text-fg">{totalPages}</span>
                    </div>
                  </div>

                  <div className="border-t border-border pt-4 mb-6">
                    <div className="flex justify-between items-center">
                      <span className="text-lg font-semibold text-fg">
                        Estimated Total
                      </span>
                      <span className="text-3xl font-bold text-primary">
                        ₹{totalAmount}
                      </span>
                    </div>
                  </div>

                  <button
                    type="button"
                    onClick={handleSubmit}
                    className="btn-primary w-full py-2.5"
                  >
                    Continue
                  </button>
                </div>
              </div>
            )}
          </div>
        )}

        {step === uploadSteps.SUMMARY && (
          <div className="space-y-6">
            <div className="card p-8 text-center">
              <div className="w-16 h-16 mx-auto rounded-full bg-success/10 flex items-center justify-center mb-4">
                <svg
                  className="w-8 h-8 text-success"
                  fill="none"
                  stroke="currentColor"
                  viewBox="0 0 24 24"
                >
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    strokeWidth={2.5}
                    d="M5 13l4 4L19 7"
                  />
                </svg>
              </div>
              <h2 className="text-3xl font-bold text-fg mb-2">
                Print Request Submitted
              </h2>
              <p className="text-text-secondary mb-6">
                Your document has been queued for printing.
              </p>

              <div className="text-center mb-8">
                <span className="text-5xl font-bold text-primary">
                  #{token}
                </span>
                <p className="text-sm text-text-secondary mt-2">
                  Token Number
                </p>
              </div>

              <div className="max-w-md mx-auto text-left space-y-3 mb-8">
                <div className="flex justify-between">
                  <span className="text-text-secondary">Document</span>
                  <span className="text-fg">{dummyFile.name}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-text-secondary">Pages</span>
                  <span className="text-fg">{dummyFile.pages}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-text-secondary">Print Type</span>
                  <span className="text-fg capitalize">
                    {printType === 'bw' ? 'Black & White' : 'Color'}
                  </span>
                </div>
                <div className="flex justify-between">
                  <span className="text-text-secondary">Copies</span>
                  <span className="text-fg">{copies}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-text-secondary">
                    Estimated Amount
                  </span>
                  <span className="text-fg font-medium">
                    ₹{totalAmount}
                  </span>
                </div>
                <div className="flex justify-between">
                  <span className="text-text-secondary">Status</span>
                  <span className="text-warning font-medium">Pending</span>
                </div>
              </div>

              <div className="flex gap-4 justify-center">
                <button
                  type="button"
                  onClick={() => navigator.clipboard.writeText(`#${token}`)}
                  className="btn-outline"
                >
                  Copy Token
                </button>
                <button
                  type="button"
                  onClick={reset}
                  className="btn-primary"
                >
                  Submit Another Document
                </button>
              </div>
            </div>

            <div className="card p-6">
              <h3 className="text-lg font-semibold text-fg mb-4">
                Check Print Status
              </h3>
              <div className="flex gap-3 max-w-md">
                <input
                  type="text"
                  placeholder="Enter token number"
                  className="flex-1 px-3 py-2 border border-border rounded-lg bg-surface text-fg focus:outline-none focus:ring-2 focus:ring-primary/20"
                />
                <button className="btn-primary whitespace-nowrap">
                  Check Status
                </button>
              </div>
            </div>
          </div>
        )}

        {step === uploadSteps.CONFIRMED && (
          <div className="text-center py-12">
            <button
              type="button"
              onClick={reset}
              className="btn-primary px-8 py-3 text-base"
            >
              Start Over
            </button>
          </div>
        )}
      </div>
    </div>
  )
}

export default App
