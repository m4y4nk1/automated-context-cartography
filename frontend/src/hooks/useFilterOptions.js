import { useEffect, useState } from 'react'
import { getFilters } from '../services/api'

/** The dimensions returned by GET /api/filters, keyed as the panel expects. */
const DIMENSIONS = {
  domains: 'domain',
  businessCriticalities: 'businessCriticality',
  lifecycleStatuses: 'lifecycleStatus',
  hostings: 'hosting',
  vendorTypes: 'vendorType',
  classifications: 'classification',
  protocols: 'protocol',
}

/**
 * Reshapes the backend FilterOptions payload into the `{ filterKey: Option[] }`
 * map used by FilterPanel, dropping dimensions with no selectable values.
 * @param {object} payload - The raw /api/filters response.
 * @returns {Record<string, Array<{value: string, label: string}>>}
 */
function toPanelOptions(payload) {
  const options = {}
  for (const [field, key] of Object.entries(DIMENSIONS)) {
    const values = payload?.[field]
    if (Array.isArray(values) && values.length > 0) {
      options[key] = values
    }
  }
  return options
}

/**
 * Fetches the selectable values for every server-known filter dimension
 * (business domain, criticality, lifecycle status, hosting, vendor type,
 * classification, protocol).
 *
 * Because the options are derived server-side from the *full* model, they stay
 * stable as filters are applied — unlike options harvested from the (already
 * filtered) graph payload.
 *
 * @param {unknown} [refreshKey] - Change this to trigger a re-fetch.
 * @returns {{ options: Record<string, Array<{value: string, label: string}>>,
 *   hasMatrixData: boolean, loading: boolean, error: unknown }} `hasMatrixData`
 *   only reflects whether any server-side option list came back non-empty —
 *   it is NOT a signal that the deleted application-matrix frames are valid,
 *   and must not be wired to FrameTabs' `hasMatrixData` prop.
 */
export function useFilterOptions(refreshKey) {
  const [options, setOptions] = useState({})
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setError(null)

    getFilters()
      .then((data) => {
        if (cancelled) return
        setOptions(toPanelOptions(data))
      })
      .catch((err) => {
        if (cancelled) return
        setError(err)
        setOptions({})
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })

    return () => {
      cancelled = true
    }
  }, [refreshKey])

  // The backend returns every list empty for datasets without matrix entities.
  return { options, hasMatrixData: Object.keys(options).length > 0, loading, error }
}

