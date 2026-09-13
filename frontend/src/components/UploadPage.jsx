import { FileSpreadsheet, ShieldCheck } from 'lucide-react'
import { useState } from 'react'
import UploadButton from './UploadButton'
import './UploadPage.css'

function UploadPage({ onUploaded }) {
  const [error, setError] = useState('')

  const handleUploaded = (report) => {
    setError('')
    onUploaded(report)
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
          <UploadButton onUploaded={handleUploaded} onError={() => setError('Upload failed. Check the file and try again.')} />
          {error && <p className="upload-page-error" role="alert">{error}</p>}
        </div>
      </section>
    </main>
  )
}

export default UploadPage