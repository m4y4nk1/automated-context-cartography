import { useEffect, useState } from 'react'
import { getGapComparison } from '../services/api'

/**
 * Fetches the declared-vs-detected data-quality gap comparison from
 * getGapComparison(). Re-fetches when `refreshKey` changes (e.g. after a new
 * dataset upload).
 *
 * @param {unknown} [refreshKey] - Change this to trigger a re-fetch.
 * @returns {{ comparison: {declaredCount: number, detectedCount: number,
 *   newlyDetected: Array<object>} | null, loading: boolean, error: unknown }}
 */
export function useGapComparison(refreshKey) {
  const [comparison, setComparison] = useState(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setError(null)

    getGapComparison()
      .then((data) => {
        if (cancelled) return
        setComparison(data ?? null)
      })
      .catch((err) => {
        if (cancelled) return
        setError(err)
        setComparison(null)
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })

    return () => {
      cancelled = true
    }
  }, [refreshKey])

  return { comparison, loading, error }
}
