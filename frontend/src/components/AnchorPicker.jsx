import { Crosshair, X } from 'lucide-react'
import './AnchorPicker.css'

/**
 * Lets a user pick a starting point — an application, a business process, a
 * domain, or an information object, depending on the active frame — and get a
 * genuinely scoped context diagram back from the server (only that anchor's
 * neighborhood, not the whole frame with elements dimmed).
 *
 * Deliberately separate from FilterPanel's free-text Search field: that one
 * highlights matches within the full frame, while this picks exactly one
 * unambiguous anchor and asks the backend for a reduced diagram.
 *
 * @param {object} props
 * @param {Array<{value: string, label: string}>} props.options - Pickable
 *   anchors: every node currently in the loaded frame (id + label).
 * @param {string|null} props.anchorId - The selected anchor, or null for the
 *   full landscape.
 * @param {number} props.depth - Hops out from the anchor to include (1-3).
 * @param {(id: string|null) => void} props.onAnchorChange
 * @param {(depth: number) => void} props.onDepthChange
 */
function AnchorPicker({ options, anchorId, depth, onAnchorChange, onDepthChange }) {
  const isFocused = Boolean(anchorId)

  return (
    <div className={`anchor-picker${isFocused ? ' anchor-picker--focused' : ''}`}>
      <Crosshair className="anchor-picker-icon" size={14} aria-hidden="true" />
      <select
        className="anchor-picker-select"
        aria-label="Focus the diagram on a specific application, process, domain, or information object"
        value={anchorId ?? ''}
        onChange={(e) => onAnchorChange(e.target.value || null)}
      >
        <option value="">Full landscape</option>
        {options.map((option) => (
          <option key={option.value} value={option.value}>{option.label}</option>
        ))}
      </select>

      {isFocused && (
        <>
          <select
            className="anchor-picker-depth"
            aria-label="How many connections out from the anchor to show"
            value={depth}
            onChange={(e) => onDepthChange(Number(e.target.value))}
          >
            <option value={1}>1 hop</option>
            <option value={2}>2 hops</option>
            <option value={3}>3 hops</option>
          </select>
          <button
            type="button"
            className="anchor-picker-clear"
            onClick={() => onAnchorChange(null)}
            title="View full landscape"
            aria-label="Clear focus and view the full landscape"
          >
            <X size={14} aria-hidden="true" />
          </button>
        </>
      )}
    </div>
  )
}

export default AnchorPicker
