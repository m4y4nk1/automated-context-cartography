import { useEffect, useState } from 'react'
import { ArrowRightLeft, Cable, GitBranch, X } from 'lucide-react'
import { findingsForEntity } from '../services/insightAdapter'
import './NodePopupDialog.css'
import './NodeDetail.css'

/** Icon shown in the header, by the edge's backend-provided `edgeTypes` category. */
const KIND_ICONS = {
  DEPENDENCY: GitBranch,
  INTERFACE: Cable,
  FLOW: ArrowRightLeft,
}

/** Human-readable type label shown as a chip in the edge title. */
const KIND_LABELS = {
  DEPENDENCY: 'Dependency',
  INTERFACE: 'Interface',
  FLOW: 'Information Flow',
}

/** Acronyms kept upper-cased rather than title-cased, matching NodeDetail's convention. */
const ACRONYMS = new Set(['PII', 'PCI', 'SAAS', 'COTS'])

function humanizeEnum(value) {
  if (!value) return value
  return value
    .split('_')
    .map((word) => (ACRONYMS.has(word) ? word : word.charAt(0) + word.slice(1).toLowerCase()))
    .join(' ')
}

function DetailRow({ label, value }) {
  if (value === null || value === undefined || value === '') return null
  return (
    <div className="detail-row">
      <span className="detail-key">{label}</span>
      <span className="detail-val">{value}</span>
    </div>
  )
}

/**
 * The type-specific rows for an edge, by its `edgeTypes` category. An edge
 * from a frame other than the application view (domain aggregate, process
 * mapping, information-object produces/consumes) has no recognized category
 * — it still gets the common From/To/Type rows above, just nothing here,
 * rather than an empty or broken dialog.
 */
function KindDetail({ edge }) {
  const kind = edge.edgeTypes?.[0]
  if (kind === 'DEPENDENCY') {
    return <DetailRow label="Dependency Criticality" value={humanizeEnum(edge.dependencyCriticality)} />
  }
  if (kind === 'INTERFACE') {
    return (
      <>
        <DetailRow label="Protocol" value={edge.protocols?.[0]} />
        <DetailRow label="Data Format" value={edge.dataFormats?.[0]} />
        <DetailRow label="Frequency" value={humanizeEnum(edge.frequencies?.[0])} />
        <DetailRow label="Status" value={humanizeEnum(edge.interfaceStatuses?.[0])} />
      </>
    )
  }
  if (kind === 'FLOW') {
    return (
      <>
        <DetailRow label="Classification" value={humanizeEnum(edge.classifications?.[0])} />
        <DetailRow label="Operation" value={humanizeEnum(edge.operations?.[0])} />
      </>
    )
  }
  return null
}

/**
 * Modal dialog for a selected edge (relationship / interface / information
 * flow, or another frame's aggregate edge) — the edge-level counterpart to
 * NodePopupDialog, reusing the same visual shell (NodePopupDialog.css) since
 * the chrome (overlay, header, close button) is identical.
 *
 * @param {object} props
 * @param {boolean} props.open - Whether the dialog is visible.
 * @param {object | null} props.edge - Selected edge data (id, source, target,
 *   label, type, edgeTypes, sourceLabel, targetLabel, plus type-specific
 *   fields), as emitted by GraphCanvas' onEdgeSelect.
 * @param {Array<object>} [props.findings] - Full findings list, used to show
 *   this edge's underlying source-record detail (same traceability as node popups).
 * @param {() => void} props.onClose - Called to dismiss the dialog.
 */
function EdgePopupDialog({ open, edge, findings = [], onClose }) {
  const [closing, setClosing] = useState(false)

  useEffect(() => {
    if (!open) return undefined
    setClosing(false)
    const handleKeyDown = (event) => {
      if (event.key === 'Escape') onClose?.()
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [open, onClose])

  if (!open || !edge) return null

  const kind = edge.edgeTypes?.[0]
  const Icon = KIND_ICONS[kind] ?? GitBranch
  const typeLabel = KIND_LABELS[kind] ?? humanizeEnum(edge.type) ?? 'Connection'
  const edgeFindings = findingsForEntity(findings, edge.id)

  const handleOverlayClick = () => onClose?.()

  return (
    <div
      className={`node-popup-overlay${closing ? ' is-closing' : ''}`}
      onClick={handleOverlayClick}
    >
      <div
        className={`node-popup-dialog${closing ? ' is-closing' : ''}`}
        role="dialog"
        aria-modal="true"
        aria-label={`Details for ${edge.label ?? edge.id}`}
        style={{ '--accent': '#0E4A47' }}
        onClick={(event) => event.stopPropagation()}
      >
        <div className="node-popup-header">
          <span className="node-popup-icon">
            <Icon size={18} />
          </span>
          <div className="node-popup-heading">
            <div className="node-popup-title">{edge.label ?? edge.id}</div>
            <span className="node-popup-chip">{typeLabel}</span>
          </div>
          <button
            type="button"
            className="node-popup-close"
            onClick={onClose}
            aria-label="Close connection details"
            title="Close"
          >
            <X size={16} />
          </button>
        </div>

        <div className="node-popup-body">
          <div className="node-detail">
            <DetailRow label="From" value={edge.sourceLabel ?? edge.source} />
            <DetailRow label="To" value={edge.targetLabel ?? edge.target} />
            <KindDetail edge={edge} />

            {edgeFindings.length > 0 && (
              <div className="node-detail-section">
                <div className="node-detail-subtitle">Source records</div>
                {edgeFindings.map((finding, index) => (
                  <p key={`${finding.type}-${index}`} className="node-detail-trace">
                    {finding.message}
                  </p>
                ))}
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  )
}

export default EdgePopupDialog
