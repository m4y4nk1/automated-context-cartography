import { useEffect, useMemo, useState } from 'react'
import { getGraph } from '../services/api'
import { toCytoscapeElements } from '../services/graphAdapter'

/**
 * Fetches the graph projection for the given frame and converts it to
 * Cytoscape elements. Re-fetches whenever the frame, server filters or
 * `refreshKey` change.
 *
 * @param {string} frame - The active observation frame slug.
 * @param {unknown} [refreshKey] - Change this to force a re-fetch (e.g. after upload).
 * @param {object} [serverFilters] - Optional backend filters; omitted when empty.
 * @returns {{ elements: Array<object>, loading: boolean, error: unknown }}
 */
export function useGraphData(frame, refreshKey, serverFilters) {
  const [elements, setElements] = useState([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)

  // Stable dependency: filters are a fresh object on every render.
  const filterKey = useMemo(() => JSON.stringify(serverFilters ?? {}), [serverFilters])

  useEffect(() => {
    if (!frame) return undefined

    let cancelled = false
    setLoading(true)
    setError(null)

    getGraph(frame, JSON.parse(filterKey))
      .then((graph) => {
        if (cancelled) return
        setElements(toCytoscapeElements(graph))
      })
      .catch((err) => {
        if (cancelled) return
        setError(err)
        setElements([])
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })

    // Ignore the result of an in-flight request if the frame changes again.
    return () => {
      cancelled = true
    }
  }, [frame, refreshKey, filterKey])

  return { elements, loading, error }
}
