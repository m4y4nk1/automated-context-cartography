import { useEffect, useState } from 'react'
import { Boxes, Building2, FileText, Workflow, X } from 'lucide-react'
import NodeDetail from './NodeDetail'
import './NodePopupDialog.css'

/** Icon shown in the header, based on the node's `type`. */
const TYPE_ICONS = {
  domain: Building2,
  process: Workflow,
  informationObject: FileText,
}

/** Human-readable type label shown as a chip in the node title. */
const TYPE_LABELS = {
  domain: 'Domain',
  process: 'Process',
  informationObject: 'Information object',
  application: 'Application',
}

/**
 * Modal dialog that displays the selected node's details (via NodeDetail)
 * when a node is clicked on the graph canvas. Purely additive UI — it does
 * not alter the existing selection/highlight logic in GraphCanvas; it just
 * reflects the currently selected node.
 *
 * @param {object} props
 * @param {boolean} props.open - Whether the dialog is visible.
 * @param {object | null} props.node - Selected node data, same shape passed
 *   to NodeDetail (id, label, type, color, businessDomain, lifecycleStatus, ...).
 * @param {string} [props.issueClasses] - Space-separated issue classes for
 *   the selected node (e.g. "gap eol"), used to render badges.
 * @param {Array<object>} [props.findings] - Full findings list, forwarded to
 *   NodeDetail so it can show each badge's underlying source-record detail.
 * @param {() => void} props.onClose - Called to dismiss the dialog (overlay
 *   click, close button, or Escape key).
 */
function NodePopupDialog({ open, node, issueClasses = '', findings = [], onClose }) {
  // Drives the exit animation: keeps the dialog mounted for one more frame
  // after `open` flips to false so the CSS transition can play out.
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

  if (!open || !node) return null

  const Icon = TYPE_ICONS[node.type] ?? Boxes
  const typeLabel = TYPE_LABELS[node.type] ?? TYPE_LABELS.application
  const accent = node.color ?? '#0E4A47'

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
        aria-label={`Details for ${node.label ?? node.id}`}
        style={{ '--accent': accent }}
        onClick={(event) => event.stopPropagation()}
      >
        <div className="node-popup-header">
          <span className="node-popup-icon">
            <Icon size={18} />
          </span>
          <div className="node-popup-heading">
            <div className="node-popup-title">{node.label ?? node.id}</div>
            <span className="node-popup-chip">{typeLabel}</span>
          </div>
          <button
            type="button"
            className="node-popup-close"
            onClick={onClose}
            aria-label="Close node details"
            title="Close"
          >
            <X size={16} />
          </button>
        </div>

        <div className="node-popup-body">
          <NodeDetail node={node} issueClasses={issueClasses} findings={findings} hideTitle />
        </div>
      </div>
    </div>
  )
}

export default NodePopupDialog
