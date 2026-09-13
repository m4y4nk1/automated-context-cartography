import { useRef, useState } from 'react'
import { LoaderCircle, Upload } from 'lucide-react'
import { uploadDataset } from '../services/api'
import './UploadButton.css'

/** File types the backend can ingest. */
const ACCEPT = '.json,.csv,.xlsx'

/**
 * Left-panel dataset uploader. Accepts a .json/.csv/.xlsx file, posts it via
 * uploadDataset(), and reports the returned validation report to the parent so
 * it can refresh the graph/insights/summary and show a toast.
 *
 * @param {object} props
 * @param {(report: object) => void} [props.onUploaded] - Called with the validation report on success.
 * @param {(error: unknown) => void} [props.onError] - Called if the upload fails.
 */
function UploadButton({ onUploaded, onError }) {
  const inputRef = useRef(null)
  const [uploading, setUploading] = useState(false)
  const [fileName, setFileName] = useState('')

  const handleChange = async (e) => {
    const file = e.target.files?.[0]
    if (!file) return

    setFileName(file.name)
    setUploading(true)
    try {
      const report = await uploadDataset(file)
      onUploaded?.(report)
    } catch (err) {
      onError?.(err)
    } finally {
      setUploading(false)
      // Reset so selecting the same file again re-triggers change.
      if (inputRef.current) inputRef.current.value = ''
    }
  }

  return (
    <div className="upload">
      <input
        ref={inputRef}
        type="file"
        accept={ACCEPT}
        className="upload-input"
        onChange={handleChange}
        disabled={uploading}
      />
      <button
        type="button"
        className="upload-btn"
        disabled={uploading}
        onClick={() => inputRef.current?.click()}
      >
        {uploading
          ? <LoaderCircle className="action-icon action-icon--spin" size={17} aria-hidden="true" />
          : <Upload className="action-icon" size={17} strokeWidth={2.2} aria-hidden="true" />}
        <span>{uploading ? 'Uploading…' : 'Upload dataset'}</span>
      </button>
      <div className="upload-hint">
        {fileName ? fileName : 'Accepts .json, .csv, .xlsx'}
      </div>
    </div>
  )
}

export default UploadButton

