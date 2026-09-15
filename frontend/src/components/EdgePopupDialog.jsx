import { useEffect } from 'react'
import { ArrowRightLeft, Cable, GitBranch, Network, Workflow, X } from 'lucide-react'
import { findingsForEntity } from '../services/insightAdapter'
import './NodePopupDialog.css'
import './NodeDetail.css'

/**
 * An application-frame edge carries a backend-provided `edgeTypes` category
 * (DEPENDENCY | INTERFACE | FLOW); every other frame's edge is identified by
 * its own `type` (processMapping, produces/consumes, domainFlow).
 */
function kindOf(edge) {
  return edge.edgeTypes?.[0] ?? edge.type
}

const KIND_ICONS = {
  DEPENDENCY: GitBranch,
  INTERFACE: Cable,
  FLOW: ArrowRightLeft,
  processMapping: Workflow,
  produces: ArrowRightLeft,
  consumes: ArrowRightLeft,
  domainFlow: Network,
}

const KIND_LABELS = {
  DEPENDENCY: 'Dependency',
  INTERFACE: 'Interface',
  FLOW: 'Information Flow',
  processMapping: 'Process Support',
  produces: 'Information Flow',
  consumes: 'Information Flow',
  domainFlow: 'Domain Coupling',
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

/** The type-specific rows for an edge; an unrecognized kind just gets the common From/To rows. */
function KindDetail({ edge }) {
  switch (kindOf(edge)) {
    case 'DEPENDENCY':
      return <DetailRow label="Dependency Criticality" value={humanizeEnum(edge.dependencyCriticality)} />
    case 'INTERFACE':
      return (
        <>
          <DetailRow label="Protocol" value={edge.protocols?.[0]} />
          <DetailRow label="Data Format" value={edge.dataFormats?.[0]} />
          <DetailRow label="Frequency" value={humanizeEnum(edge.frequencies?.[0])} />
          <DetailRow label="Status" value={humanizeEnum(edge.interfaceStatuses?.[0])} />
        </>
      )
    case 'FLOW':
      return (
        <>
          <DetailRow label="Classification" value={humanizeEnum(edge.classifications?.[0])} />
          <DetailRow label="Operation" value={humanizeEnum(edge.operations?.[0])} />
        </>
      )
    case 'processMapping':
      return (
        <>
          <DetailRow label="Role" value={humanizeEnum(edge.roleOfApplication)} />
          <DetailRow label="Process Criticality" value={humanizeEnum(edge.processCriticality)} />
        </>
      )
    case 'produces':
    case 'consumes':
      return (
        <>
          <DetailRow label="Flow" value={edge.flowId} />
          <DetailRow label="Operation" value={humanizeEnum(edge.operation)} />
          <DetailRow label="Interface" value={edge.interfaceId} />
          <DetailRow label="Interface Status" value={humanizeEnum(edge.interfaceStatus)} />
        </>
      )
    case 'domainFlow':
      return (
        <>
          <DetailRow label="Relationships" value={edge.relationshipCount} />
          <DetailRow label="Interfaces" value={edge.interfaceCount} />
          <DetailRow label="Information Flows" value={edge.flowCount} />
        </>
      )
    default:
      return null
  }
}

/**
 * Modal dialog for a selected edge in any frame — the edge-level counterpart
 * to NodePopupDialog, reusing the same visual shell (NodePopupDialog.css)
 * since the chrome (overlay, header, close button) is identical.
 *
 * @param {object} props
 * @param {boolean} props.open - Whether the dialog is visible.
 * @param {object | null} props.edge - Selected edge data (id, source, target,
 *   label, type, sourceLabel, targetLabel, plus type-specific fields), as
 *   emitted by GraphCanvas' onEdgeSelect.
 * @param {Array<object>} [props.findings] - Full findings list, used to show
 *   this edge's underlying source-record detail (same traceability as node popups).
 * @param {() => void} props.onClose - Called to dismiss the dialog.
 */
function EdgePopupDialog({ open, edge, findings = [], onClose }) {
  useEffect(() => {
    if (!open) return undefined
    const handleKeyDown = (event) => {
      if (event.key === 'Escape') onClose?.()
    }
    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [open, onClose])

  if (!open || !edge) return null

  const kind = kindOf(edge)
  const Icon = KIND_ICONS[kind] ?? GitBranch
  const typeLabel = KIND_LABELS[kind] ?? humanizeEnum(edge.type) ?? 'Connection'
  // An information-flow frame edge's own id is "<flowId>:produces" / ":consumes";
  // findings name the underlying flow record, so trace by that instead.
  const recordId = edge.flowId ?? edge.id
  const edgeFindings = findingsForEntity(findings, recordId)

  return (
    <div className="node-popup-overlay" onClick={() => onClose?.()}>
      <div
        className="node-popup-dialog"
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
