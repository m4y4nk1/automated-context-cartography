import { useEffect, useState } from 'react'
import { getSummary } from '../services/api'

/**
 * Fetches the natural-language landscape summary from getSummary().
 * Re-fetches when `refreshKey` changes (e.g. after a new dataset upload).
 *
 * @param {unknown} [refreshKey] - Change this to trigger a re-fetch.
 * @returns {{ summary: string, loading: boolean, error: unknown }}
 */
export function useSummary(refreshKey) {
  const [summary, setSummary] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setError(null)

    getSummary()
      .then((data) => {
        if (cancelled) return
        setSummary(data?.summary ?? '')
      })
      .catch((err) => {
        if (cancelled) return
        setError(err)
        setSummary('')
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })

    return () => {
      cancelled = true
    }
  }, [refreshKey])

  return { summary, loading, error }
}

