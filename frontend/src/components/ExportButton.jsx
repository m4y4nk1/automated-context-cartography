import { useEffect, useRef, useState } from 'react'
import { ChevronDown, Download, FileImage, FileText, FileType, LoaderCircle } from 'lucide-react'
import { jsPDF } from 'jspdf'
import './ExportButton.css'

const OPTIONS = [
  { type: 'png', label: 'PNG image', icon: FileImage },
  { type: 'pdf', label: 'PDF file', icon: FileText },
  { type: 'svg', label: 'SVG file', icon: FileType },
]

/** Triggers a browser download for a Blob or data-URI string. */
function triggerDownload(source, filename) {
  const isString = typeof source === 'string'
  const url = isString ? source : URL.createObjectURL(source)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  document.body.appendChild(a)
  a.click()
  a.remove()
  if (!isString) URL.revokeObjectURL(url)
}

/**
 * Top-bar export control with a PNG / PDF / SVG dropdown. All three formats
 * are rendered client-side directly from the live Cytoscape instance:
 * - PNG uses cy.png().
 * - SVG uses cy.svg() (via the cytoscape-svg extension registered in
 *   GraphCanvas).
 * - PDF embeds a high-res PNG snapshot of the canvas into a single-page PDF
 *   sized to the diagram's aspect ratio (via jsPDF), so no backend call is
 *   needed for any format.
 *
 * @param {object} props
 * @param {() => object | null} props.getCy - Returns the live Cytoscape instance.
 * @param {(error: unknown) => void} [props.onError] - Called on export failure.
 */
function ExportButton({ getCy, onError }) {
  const [open, setOpen] = useState(false)
  const [busy, setBusy] = useState(false)
  const rootRef = useRef(null)

  // Close the dropdown on outside click.
  useEffect(() => {
    const onDocMouseDown = (e) => {
      if (rootRef.current && !rootRef.current.contains(e.target)) setOpen(false)
    }
    document.addEventListener('mousedown', onDocMouseDown)
    return () => document.removeEventListener('mousedown', onDocMouseDown)
  }, [])

  const handleExport = async (type) => {
    setOpen(false)

    const cy = getCy?.()
    if (!cy) {
      onError?.(new Error('Graph is not ready to export.'))
      return
    }

    setBusy(true)
    try {
      if (type === 'png') {
        const uri = cy.png({ full: true, scale: 2, bg: '#ffffff' })
        triggerDownload(uri, 'ea-context-diagram.png')
        return
      }

      if (type === 'svg') {
        const svgMarkup = cy.svg({ full: true, scale: 1, bg: '#ffffff' })
        const blob = new Blob([svgMarkup], { type: 'image/svg+xml;charset=utf-8' })
        triggerDownload(blob, 'ea-context-diagram.svg')
        return
      }

      if (type === 'pdf') {
        const uri = cy.png({ full: true, scale: 2, bg: '#ffffff' })
        const bounds = cy.elements(':visible').boundingBox()
        const width = Math.max(bounds.w, 1)
        const height = Math.max(bounds.h, 1)
        const pdf = new jsPDF({
          orientation: width >= height ? 'landscape' : 'portrait',
          unit: 'pt',
          format: [width, height],
        })
        pdf.addImage(uri, 'PNG', 0, 0, width, height)
        pdf.save('ea-context-diagram.pdf')
        return
      }
    } catch (err) {
      onError?.(err)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="export" ref={rootRef}>
      <button
        type="button"
        className="export-btn"
        disabled={busy}
        aria-haspopup="menu"
        aria-expanded={open}
        onClick={() => setOpen((o) => !o)}
      >
        {busy
          ? <LoaderCircle className="action-icon action-icon--spin" size={16} aria-hidden="true" />
          : <Download className="action-icon" size={16} strokeWidth={2.2} aria-hidden="true" />}
        <span>{busy ? 'Exporting…' : 'Export'}</span>
        <ChevronDown className={`export-caret${open ? ' is-open' : ''}`} size={14} aria-hidden="true" />
      </button>
      {open && (
        <ul className="export-menu" role="menu">
          {OPTIONS.map((o) => (
            <li key={o.type} role="none">
              <button
                type="button"
                role="menuitem"
                className="export-item"
                onClick={() => handleExport(o.type)}
              >
                <o.icon className="export-item-icon" size={15} aria-hidden="true" />
                <span>{o.label}</span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

export default ExportButton



