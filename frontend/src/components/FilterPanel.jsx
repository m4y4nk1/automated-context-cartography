import { useState } from 'react'
import { ChevronDown } from 'lucide-react'
import { MATRIX_FRAMES } from '../services/filterState'
import './FilterPanel.css'

/** Lifecycle options for the existing single-select dropdown. */
const LIFECYCLE_OPTIONS = [
  { value: 'ACTIVE', label: 'Active' },
  { value: 'PLAN', label: 'Plan' },
  { value: 'PHASE_IN', label: 'Phase In' },
  { value: 'PHASE_OUT', label: 'Phase Out' },
  { value: 'END_OF_LIFE', label: 'End of Life' },
]

/** Viewpoints supported by the application matrix. */
const VIEWPOINT_OPTIONS = [
  { value: 'Current', label: 'Current' },
  { value: 'Target', label: 'Target' },
  { value: 'Reference', label: 'Reference' },
]

/** Process hierarchy levels. */
const PROCESS_LEVEL_OPTIONS = [
  { value: 'L1', label: 'L1' },
  { value: 'L2', label: 'L2' },
  { value: 'L3', label: 'L3' },
]

/**
 * Frame-aware graph controls. The primary dropdown follows the active frame;
 * lifecycle and issue filters remain specific to applications, the Auriga
 * checkbox-group dimensions appear on the application/information-flow
 * frames as noted per-group, and the (now-dormant) application-matrix
 * dimensions still render only for matrix frames, which no current backend
 * `Frame` ever is.
 *
 * @param {object} props
 * @param {typeof EMPTY_FILTERS} props.filters - Current filter values.
 * @param {(filters: typeof EMPTY_FILTERS) => void} props.onChange - Emits the next filter state.
 * @param {() => void} props.onReset - Clears all filters.
 * @param {string} props.frame - Active graph frame.
 * @param {Array<{value: string, label: string}>} [props.options] - Primary filter options.
 * @param {object} [props.serverOptions] - Server-derived option lists keyed by
 *   dimension (business criticality, lifecycle status, hosting, classification,
 *   plus the legacy matrix dimensions).
 */
function FilterPanel({ filters, onChange, onReset, frame = 'application', options = [], serverOptions = {} }) {
  const [hostingExpanded, setHostingExpanded] = useState(false)
  const update = (key, value) => onChange({ ...filters, [key]: value })
  const toggleArrayValue = (key, value) => {
    const current = filters[key] ?? []
    const next = current.includes(value)
      ? current.filter((v) => v !== value)
      : [...current, value]
    update(key, next)
  }

  const isApplication = frame === 'application'
  const isInfoFlow = frame === 'infoflow'
  const isMatrixFrame = MATRIX_FRAMES.includes(frame)
  const filterLabel = frame === 'process'
    ? 'Filter by Process'
    : frame === 'infoflow'
      ? 'Filter by Information'
      : 'Filter by Domain'
  const allLabel = frame === 'process'
    ? 'All processes'
    : frame === 'infoflow'
      ? 'All information objects'
      : 'All domains'
  const searchPlaceholder = frame === 'process'
    ? 'Search a process…'
    : frame === 'infoflow'
      ? 'Search information…'
      : frame === 'domain'
        ? 'Search a domain…'
        : 'Search an application…'

  /** Renders one optional matrix dropdown, or a free-text box when no options exist. */
  const renderMatrixSelect = (key, label, placeholder, presetOptions) => {
    const opts = presetOptions ?? serverOptions[key] ?? []
    return (
      <div key={key}>
        <h3 className="filter-label">{label}</h3>
        {opts.length > 0 ? (
          <select
            className="filter-input"
            value={filters[key] ?? ''}
            onChange={(e) => update(key, e.target.value)}
          >
            <option value="">{placeholder}</option>
            {opts.map((o) => (
              <option key={o.value} value={o.value}>
                {o.label}
              </option>
            ))}
          </select>
        ) : (
          <input
            type="text"
            className="filter-input"
            placeholder={placeholder}
            value={filters[key] ?? ''}
            onChange={(e) => update(key, e.target.value)}
          />
        )}
      </div>
    )
  }

  /** Renders a checkbox per option for a multi-select (array-valued) filter key. */
  const renderCheckboxList = (key, opts) => {
    const selected = filters[key] ?? []
    return opts.map((o) => (
      <label className="filter-check" key={o.value}>
        <input
          type="checkbox"
          checked={selected.includes(o.value)}
          onChange={() => toggleArrayValue(key, o.value)}
        />
        {o.label}
      </label>
    ))
  }

  const businessCriticalityOptions = serverOptions.businessCriticality ?? []
  const lifecycleStatusOptions = serverOptions.lifecycleStatus ?? []
  const hostingOptions = serverOptions.hosting ?? []
  const classificationOptions = serverOptions.classification ?? []

  return (
    <div className="filter-panel">
      {!isMatrixFrame && (
        <>
          <h3 className="filter-label">{filterLabel}</h3>
          <select
            className="filter-input"
            value={filters.domain}
            onChange={(e) => update('domain', e.target.value)}
          >
            <option value="">{allLabel}</option>
            {options.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </>
      )}

      {isApplication && (
        <>
          <h3 className="filter-label">Filter by Lifecycle</h3>
          <select
            className="filter-input"
            value={filters.lifecycle}
            onChange={(e) => update('lifecycle', e.target.value)}
          >
            <option value="">All statuses</option>
            {LIFECYCLE_OPTIONS.map((o) => (
              <option key={o.value} value={o.value}>
                {o.label}
              </option>
            ))}
          </select>

          <h3 className="filter-label">Quick Issue Filters</h3>
          <label className="filter-check">
            <input
              type="checkbox"
              checked={filters.gapOnly}
              onChange={(e) => update('gapOnly', e.target.checked)}
            />
            Ownership gaps only
          </label>
          <label className="filter-check">
            <input
              type="checkbox"
              checked={filters.eolOnly}
              onChange={(e) => update('eolOnly', e.target.checked)}
            />
            End-of-life only
          </label>

          {businessCriticalityOptions.length > 0 && (
            <div>
              <h3 className="filter-label">Business Criticality</h3>
              {renderCheckboxList('businessCriticality', businessCriticalityOptions)}
            </div>
          )}

          {lifecycleStatusOptions.length > 0 && (
            <div>
              <h3 className="filter-label">Lifecycle Status</h3>
              {renderCheckboxList('lifecycleStatuses', lifecycleStatusOptions)}
            </div>
          )}

          {hostingOptions.length > 0 && (
            <div>
              <button
                type="button"
                className="filter-collapse-toggle"
                onClick={() => setHostingExpanded((c) => !c)}
                aria-expanded={hostingExpanded}
              >
                <span className="filter-label">Hosting</span>
                <ChevronDown
                  className={`filter-collapse-chevron${hostingExpanded ? ' is-open' : ''}`}
                  size={16}
                  aria-hidden="true"
                />
              </button>
              {hostingExpanded && renderCheckboxList('hosting', hostingOptions)}
            </div>
          )}
        </>
      )}

      {isInfoFlow && classificationOptions.length > 0 && (
        <div>
          <h3 className="filter-label">Classification</h3>
          {renderCheckboxList('classification', classificationOptions)}
        </div>
      )}

      {isMatrixFrame && (
        <div className="filter-matrix">
          <p className="filter-section">Scope</p>
          {renderMatrixSelect('landscape', 'Landscape', 'All landscapes')}
          {renderMatrixSelect('site', 'Site', 'All sites')}
          {renderMatrixSelect('brand', 'Brand', 'All brands')}

          <p className="filter-section">Business</p>
          {renderMatrixSelect('businessArea', 'Business Area', 'All business areas')}
          {renderMatrixSelect('processLevel', 'Process Level', 'All levels', PROCESS_LEVEL_OPTIONS)}
          {renderMatrixSelect('activity', 'Activity', 'All activities')}
          {renderMatrixSelect('capability', 'Capability', 'All capabilities')}

          <p className="filter-section">Application</p>
          {renderMatrixSelect('application', 'Application', 'All applications')}
          {renderMatrixSelect('lifecycle', 'Lifecycle', 'All statuses', LIFECYCLE_OPTIONS)}

          {frame === 'dependency' && (
            <>
              <p className="filter-section">Dependency</p>
              {renderMatrixSelect('dependencyType', 'Dependency Type', 'All types')}
              {renderMatrixSelect('criticality', 'Criticality', 'All criticalities')}
            </>
          )}

          {renderMatrixSelect('viewpoint', 'Viewpoint', 'All viewpoints', VIEWPOINT_OPTIONS)}
        </div>
      )}

      <h3 className="filter-label">Search</h3>
      <input
        type="text"
        className="filter-input"
        placeholder={searchPlaceholder}
        value={filters.search}
        onChange={(e) => update('search', e.target.value)}
      />

      <button
        type="button"
        className="filter-reset"
        onClick={onReset}
      >
        ↺ Reset view
      </button>
    </div>
  )
}

export default FilterPanel
