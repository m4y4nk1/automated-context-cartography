import { useEffect } from 'react'
import './Toast.css'

/**
 * A small auto-dismissing toast/banner.
 *
 * @param {object} props
 * @param {string} props.message - The message to display.
 * @param {'success'|'warning'|'error'} [props.variant] - Visual style.
 * @param {number} [props.duration] - Auto-dismiss delay in ms (0 disables).
 * @param {() => void} props.onClose - Called when dismissed.
 */
function Toast({ message, variant = 'success', duration = 6000, onClose }) {
  useEffect(() => {
    if (!duration) return undefined
    const timer = setTimeout(onClose, duration)
    return () => clearTimeout(timer)
  }, [message, duration, onClose])

  return (
    <div className={`toast toast--${variant}`} role="status" aria-live="polite">
      <span className="toast-message">{message}</span>
      <button type="button" className="toast-close" aria-label="Dismiss" onClick={onClose}>
        ×
      </button>
    </div>
  )
}

export default Toast

