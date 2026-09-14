import { useEffect, useState } from 'react'
import { LoaderCircle } from 'lucide-react'
import './LoadingScreen.css'

const DURATION_MS = 3500
const LINE_INTERVAL_MS = 900

const LINES = [
  'Mapping your applications…',
  'Tracing dependencies across your landscape…',
  'Connecting the dots between systems…',
  'Surfacing AI insights…',
  'Assembling your context diagram…',
]

/**
 * A brief, purely cosmetic full-screen hand-off between the upload flow and
 * the workspace — the workspace's own panels each already have their own
 * loading state once mounted, so this isn't gated on real data readiness,
 * just a fixed-duration transition so the jump from the validation summary
 * into a fully-populated app doesn't feel like an instant hard cut.
 *
 * @param {object} props
 * @param {() => void} props.onDone - Called once the fixed duration elapses.
 */
function LoadingScreen({ onDone }) {
  const [lineIndex, setLineIndex] = useState(0)

  useEffect(() => {
    const timer = setTimeout(onDone, DURATION_MS)
    const interval = setInterval(() => {
      setLineIndex((index) => (index + 1) % LINES.length)
    }, LINE_INTERVAL_MS)
    return () => {
      clearTimeout(timer)
      clearInterval(interval)
    }
  }, [onDone])

  return (
    <main className="loading-screen" role="status" aria-live="polite">
      <div className="loading-screen-header">
        <span className="loading-screen-brand">Contexa AI</span>
      </div>
      <div className="loading-screen-content">
        <LoaderCircle className="loading-screen-spinner" size={44} strokeWidth={2.2} aria-hidden="true" />
        <h1>Your diagrams are generating</h1>
        <p key={lineIndex} className="loading-screen-line">{LINES[lineIndex]}</p>
      </div>
    </main>
  )
}

export default LoadingScreen
