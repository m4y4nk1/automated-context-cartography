import { useEffect, useState } from 'react'
import { getInsights } from '../services/api'

/**
 * Fetches insight findings from getInsights(). Re-fetches when `refreshKey`
 * changes (e.g. after a new dataset upload).
 *
 * @param {unknown} [refreshKey] - Change this to trigger a re-fetch.
 * @returns {{ findings: Array<object>, loading: boolean, error: unknown }}
 */
export function useInsights(refreshKey) {
  const [findings, setFindings] = useState([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setError(null)

    getInsights()
      .then((data) => {
        if (cancelled) return
        setFindings(Array.isArray(data) ? data : [])
      })
      .catch((err) => {
        if (cancelled) return
        setError(err)
        setFindings([])
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })

    return () => {
      cancelled = true
    }
  }, [refreshKey])

  return { findings, loading, error }
}

