import {
  AppWindow,
  Boxes,
  Cable,
  Globe,
  Grid3x3,
  Layers,
  ListChecks,
  MapPin,
  Network,
  Tag,
  Workflow,
} from 'lucide-react'
import './FrameTabs.css'
import HorizontalScroller from './HorizontalScroller'

/**
 * The observation frames, mapped to the backend API slugs.
 * The original four come first; application-matrix frames are appended and are
 * only shown when the loaded dataset carries matrix data.
 * @see com.vw.eacontext.dto.Frame
 */
const FRAMES = [
  { slug: 'application', label: 'Application', icon: AppWindow },
  { slug: 'domain', label: 'Domain', icon: Boxes },
  { slug: 'process', label: 'Process', icon: Workflow },
  { slug: 'infoflow', label: 'Information Flow', icon: Cable },
  { slug: 'landscape', label: 'Landscape', icon: Globe, matrix: true },
  { slug: 'site', label: 'Site', icon: MapPin, matrix: true },
  { slug: 'brand', label: 'Brand', icon: Tag, matrix: true },
  { slug: 'activity', label: 'Activity', icon: ListChecks, matrix: true },
  { slug: 'capability', label: 'Capability', icon: Layers, matrix: true },
  { slug: 'matrix', label: 'Matrix', icon: Grid3x3, matrix: true },
  { slug: 'dependency', label: 'Dependency', icon: Network, matrix: true },
]

/**
 * Row of tabs for switching the active observation frame.
 *
 * @param {object} props
 * @param {string} [props.activeFrame] - Slug of the active frame.
 * @param {(frame: string) => void} [props.onFrameChange] - Called with the slug when the frame changes.
 * @param {boolean} [props.hasMatrixData] - Whether to show the application-matrix frames.
 */
function FrameTabs({ activeFrame = 'application', onFrameChange, hasMatrixData = false }) {
  const handleClick = (slug) => {
    if (slug === activeFrame) return
    onFrameChange?.(slug)
  }

  const visibleFrames = FRAMES.filter((frame) => !frame.matrix || hasMatrixData)

  return (
    <HorizontalScroller
      className="frame-tabs-scroller"
      contentClassName="frame-tabs"
      role="tablist"
      ariaLabel="Observation frame"
    >
      {visibleFrames.map(({ slug, label, icon: Icon }) => {
        const isActive = slug === activeFrame
        return (
          <button
            key={slug}
            type="button"
            role="tab"
            aria-selected={isActive}
            className={`frame-tab${isActive ? ' frame-tab--active' : ''}`}
            onClick={() => handleClick(slug)}
          >
            <Icon className="frame-tab-icon" size={16} strokeWidth={2} aria-hidden="true" />
            <span>{label}</span>
          </button>
        )
      })}
    </HorizontalScroller>
  )
}

export { FRAMES }
export default FrameTabs
