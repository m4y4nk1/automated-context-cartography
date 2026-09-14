import { FileSpreadsheet, ShieldCheck } from 'lucide-react'
import { useState } from 'react'
import UploadButton from './UploadButton'
import ValidationSummary from './ValidationSummary'
import './UploadPage.css'

/** Backend message for a genuinely unrecoverable file (wrong type, corrupt, unreadable). */
function fileLevelErrorMessage(err) {
  const backendMessage = err?.response?.data?.message ?? err?.response?.data?.error
  return typeof backendMessage === 'string' && backendMessage.trim()
    ? backendMessage
    : 'Upload failed. Check the file and try again.'
}

/**
 * Upload flow: pick a file -> parse -> show a validation summary before
 * handing off to the workspace. A file-level failure (wrong format, corrupt
 * archive, nothing recognizable at all) never produces a report — it's
 * surfaced right here as an error and the uploader stays put. A file that
 * parses at all always reaches the summary step next, even when it has zero
 * issues, so "what happened to my upload" is never a silent jump straight
 * into the workspace.
 */
function UploadPage({ onUploaded }) {
  const [error, setError] = useState('')
  const [pendingReport, setPendingReport] = useState(null)

  const handleUploaded = (report) => {
    setError('')
    setPendingReport(report)
  }

  const handleUploadError = (err) => {
    setError(fileLevelErrorMessage(err))
  }

  const handleUploadDifferent = () => {
    setPendingReport(null)
    setError('')
  }

  if (pendingReport) {
    return (
      <main className="upload-page">
        <header className="upload-page-header">
          <span className="upload-page-brand">Contexa AI</span>
          <span className="upload-page-status">
            <ShieldCheck size={15} aria-hidden="true" />
            Secure import
          </span>
        </header>
        <ValidationSummary
          report={pendingReport}
          onContinue={() => onUploaded(pendingReport)}
          onUploadDifferent={handleUploadDifferent}
        />
      </main>
    )
  }

  return (
    <main className="upload-page">
      <header className="upload-page-header">
        <span className="upload-page-brand">Contexa AI</span>
        <span className="upload-page-status">
          <ShieldCheck size={15} aria-hidden="true" />
          Secure import
        </span>
      </header>

      <section className="upload-page-content" aria-labelledby="upload-title">
        <div className="upload-page-intro">
          <span className="upload-page-kicker">Start a new context map</span>
          <h1 id="upload-title">Upload your enterprise data sheet</h1>
          <p>Select a supported data file to build the graph and generate AI insights.</p>
        </div>

        <div className="upload-tool">
          <div className="upload-tool-icon" aria-hidden="true">
            <FileSpreadsheet size={34} strokeWidth={1.7} />
          </div>
          <h2>Choose a data sheet</h2>
          <p className="upload-tool-copy">JSON, CSV, or Excel up to your server limit</p>
          <UploadButton onUploaded={handleUploaded} onError={handleUploadError} />
          {error && <p className="upload-page-error" role="alert">{error}</p>}
        </div>
      </section>
    </main>
  )
}

export default UploadPage