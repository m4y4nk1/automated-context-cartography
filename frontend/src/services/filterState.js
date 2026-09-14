/**
 * Filter-state shape and helpers shared between FilterPanel.jsx and App.jsx.
 * Kept in their own file rather than FilterPanel.jsx so that component file
 * can stay component-only (Vite Fast Refresh requirement) — same pattern as
 * services/graphViewport.js.
 */

/**
 * Frames driven by the (deleted) application-matrix backend extension. No
 * current `Frame` slug ever matches one of these, so the matrix-only section
 * in FilterPanel is permanently dormant; left in place rather than removed
 * since it's inert, not broken, and that pass was additive-only.
 */
export const MATRIX_FRAMES = ['landscape', 'site', 'brand', 'activity', 'capability', 'matrix', 'dependency']

/** Filter keys sent to the backend as query parameters. */
const SERVER_FILTER_KEYS = [
  'landscape', 'site', 'brand', 'businessArea', 'processLevel', 'activity',
  'capability', 'application', 'lifecycle', 'dependencyType', 'criticality', 'viewpoint',
]

/** The empty/default filter state. */
export const EMPTY_FILTERS = {
  domain: '',
  lifecycle: '',
  gapOnly: false,
  eolOnly: false,
  search: '',
  // Auriga dimensions (checkbox groups): arrays, empty = no restriction.
  businessCriticality: [],
  lifecycleStatuses: [],
  hosting: [],
  classification: [],
  // Application-matrix dimensions (all optional, all no-op when blank).
  landscape: '',
  site: '',
  brand: '',
  businessArea: '',
  processLevel: '',
  activity: '',
  capability: '',
  application: '',
  dependencyType: '',
  criticality: '',
  viewpoint: '',
}

/**
 * Extracts only the populated backend filters from the panel state.
 * @param {typeof EMPTY_FILTERS} filters
 * @param {string} frame - Active frame; matrix dimensions are only sent for matrix frames.
 * @returns {Record<string, string>} Populated server filters.
 */
export function toServerFilters(filters, frame) {
  if (!MATRIX_FRAMES.includes(frame)) return {}
  const result = {}
  for (const key of SERVER_FILTER_KEYS) {
    const value = filters?.[key]
    if (typeof value === 'string' && value.trim() !== '') {
      result[key] = value.trim()
    }
  }
  return result
}
