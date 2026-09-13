import { useEffect, useRef, useState } from 'react'
import { ChevronLeft, ChevronRight } from 'lucide-react'
import './HorizontalScroller.css'

function HorizontalScroller({
  children,
  className = '',
  contentClassName = '',
  role,
  ariaLabel,
}) {
  const viewportRef = useRef(null)
  const [scrollState, setScrollState] = useState({
    hasOverflow: false,
    canScrollLeft: false,
    canScrollRight: false,
  })

  useEffect(() => {
    const viewport = viewportRef.current
    if (!viewport) return undefined

    const updateScrollState = () => {
      const maxScrollLeft = viewport.scrollWidth - viewport.clientWidth
      setScrollState({
        hasOverflow: maxScrollLeft > 1,
        canScrollLeft: viewport.scrollLeft > 1,
        canScrollRight: viewport.scrollLeft < maxScrollLeft - 1,
      })
    }

    updateScrollState()
    viewport.addEventListener('scroll', updateScrollState, { passive: true })

    const resizeObserver = new ResizeObserver(updateScrollState)
    resizeObserver.observe(viewport)
    if (viewport.firstElementChild) resizeObserver.observe(viewport.firstElementChild)

    return () => {
      viewport.removeEventListener('scroll', updateScrollState)
      resizeObserver.disconnect()
    }
  }, [children])

  const scroll = (direction) => {
    const viewport = viewportRef.current
    if (!viewport) return
    viewport.scrollBy({
      left: direction * Math.max(viewport.clientWidth * 0.75, 160),
      behavior: 'smooth',
    })
  }

  return (
    <div className={`horizontal-scroller${scrollState.hasOverflow ? ' has-overflow' : ''}${className ? ` ${className}` : ''}`}>
      {scrollState.hasOverflow && (
        <button
          type="button"
          className="horizontal-scroller-arrow"
          onClick={() => scroll(-1)}
          disabled={!scrollState.canScrollLeft}
          aria-label={`Scroll ${ariaLabel ?? 'options'} left`}
          title="Scroll left"
        >
          <ChevronLeft size={18} aria-hidden="true" />
        </button>
      )}
      <div
        ref={viewportRef}
        className="horizontal-scroller-viewport"
        role={role}
        aria-label={ariaLabel}
      >
        <div className={`horizontal-scroller-content${contentClassName ? ` ${contentClassName}` : ''}`}>
          {children}
        </div>
      </div>
      {scrollState.hasOverflow && (
        <button
          type="button"
          className="horizontal-scroller-arrow"
          onClick={() => scroll(1)}
          disabled={!scrollState.canScrollRight}
          aria-label={`Scroll ${ariaLabel ?? 'options'} right`}
          title="Scroll right"
        >
          <ChevronRight size={18} aria-hidden="true" />
        </button>
      )}
    </div>
  )
}

export default HorizontalScroller