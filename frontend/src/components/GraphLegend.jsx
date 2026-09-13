import { useState } from 'react'
import { Info, ChevronDown } from 'lucide-react'
import './GraphLegend.css'

/**
 * Ring color/style per issue class, mirroring the selectors declared in
 * GraphCanvas' baseStyle() (copy, not shared import — matches the existing
 * duplication between DashboardCards/ArchitectureHealth's own label maps in
 * this codebase). Labels mirror NodeDetail's BADGES map.
 */
const RING_ITEMS = [
  { cls: 'gap', color: '#df6249', style: 'solid', label: 'Ownership gap' },
  { cls: 'eol', color: '#c2415d', style: 'dashed', label: 'Lifecycle risk (EoL provider)' },
  { cls: 'duplicate', color: '#ea580c', style: 'dashed', label: 'Duplicate application' },
  { cls: 'orphan', color: '#64748b', style: 'double', label: 'Orphan application' },
  { cls: 'lifecycle-risk', color: '#f59e0b', style: 'solid', label: 'Lifecycle risk' },
  { cls: 'broken-ref', color: '#dc2626', style: 'solid', label: 'Broken reference' },
  { cls: 'unmapped', color: '#94a3b8', style: 'dotted', label: 'Unmapped process application' },
  { cls: 'circular', color: '#7c3aed', style: 'double', label: 'Circular dependency' },
  { cls: 'orphan-interface', color: '#68767e', style: 'dotted', label: 'Dangling interface consumer' },
  { cls: 'deprecated-interface', color: '#8b5cf6', style: 'dotted', label: 'Deprecated interface in use' },
  { cls: 'spof', color: '#2563a6', style: 'dashed', label: 'Hub / single point of failure' },
  { cls: 'sensitive-flow', color: '#be123c', style: 'double', label: 'Sensitive data risk' },
]

/**
 * Small, collapsible on-canvas key explaining the graph's visual language —
 * edge line-styles and issue-ring colors — none of which were otherwise
 * explained anywhere in the UI. Purely additive: an overlay inside
 * GraphCanvas's existing wrapper, touches no other component.
 *
 * @param {object} props
 * @param {string} props.frame - The active frame; the edge-type key only
 *   applies where DEPENDS_ON/USES relationship edges are actually rendered
 *   (the application frame).
 */
function GraphLegend({ frame }) {
  const [open, setOpen] = useState(false)

  return (
    <div className={`graph-legend${open ? ' is-open' : ''}`}>
      <button
        type="button"
        className="graph-legend-toggle"
        onClick={() => setOpen((current) => !current)}
        aria-expanded={open}
        title={open ? 'Hide legend' : 'Show legend'}
      >
        <Info size={14} aria-hidden="true" />
        <span>Legend</span>
        <ChevronDown className={`graph-legend-chevron${open ? ' is-open' : ''}`} size={13} aria-hidden="true" />
      </button>

      {open && (
        <div className="graph-legend-body">
          {frame === 'application' && (
            <div className="graph-legend-section">
              <div className="graph-legend-heading">Edges</div>
              <div className="graph-legend-row">
                <span className="graph-legend-line graph-legend-line--solid" aria-hidden="true" />
                <span>Depends on (hard dependency)</span>
              </div>
              <div className="graph-legend-row">
                <span className="graph-legend-line graph-legend-line--dashed" aria-hidden="true" />
                <span>Uses (soft dependency)</span>
              </div>
            </div>
          )}

          <div className="graph-legend-section">
            <div className="graph-legend-heading">Issue rings</div>
            {RING_ITEMS.map((item) => (
              <div className="graph-legend-row" key={item.cls}>
                <span
                  className="graph-legend-swatch"
                  style={{ borderColor: item.color, borderStyle: item.style }}
                  aria-hidden="true"
                />
                <span>{item.label}</span>
              </div>
            ))}
            <div className="graph-legend-row graph-legend-row--note">
              <span className="graph-legend-swatch graph-legend-swatch--thick" aria-hidden="true" />
              <span>Thicker ring = more than one issue (click the node for the full list)</span>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}

export default GraphLegend
