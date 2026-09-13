import { useEffect, useMemo, useRef, useState } from 'react'
import { ListFilter, PanelRightOpen, X } from 'lucide-react'
import DashboardCards from './components/DashboardCards'
import FrameTabs from './components/FrameTabs'
import FilterPanel, { EMPTY_FILTERS, toServerFilters } from './components/FilterPanel'
import ExportButton from './components/ExportButton'
import GraphCanvas from './components/GraphCanvas'
import InsightsPanel from './components/InsightsPanel'
import NodeDetail from './components/NodeDetail'
import NodePopupDialog from './components/NodePopupDialog'
import Toast from './components/Toast'
import UploadButton from './components/UploadButton'
import UploadPage from './components/UploadPage'
import { useGraphData } from './hooks/useGraphData'
import { useFilterOptions } from './hooks/useFilterOptions'
import { useGapComparison } from './hooks/useGapComparison'
import { useInsights } from './hooks/useInsights'
import { useSummary } from './hooks/useSummary'
import { nodeClassesFromFindings } from './services/insightAdapter'
import { fitReadable } from './services/graphViewport'
import './App.css'

const FRAME_LABELS = {
  application: 'Application',
  domain: 'Domain',
  process: 'Process',
  infoflow: 'Information Flow',
}

/**
 * Only these 2 finding types are, by definition, a reference to a record the
 * backend could never resolve anywhere (the whole point of the finding is
 * "this id points at something that doesn't exist"): BROKEN_INFORMATION_FLOW_REFERENCE's
 * only related id is the flow itself (no app id at all — see
 * BrokenInformationFlowReferenceDetector), and UNMAPPED_PROCESS_APPLICATION's
 * process mapping is dropped entirely by GraphProjectionService.businessProcessView
 * whenever its app is a ghost, so even the process itself never becomes a node.
 * Every other "ghost reference" type (broken relationships, dangling interface
 * consumers) also carries a second, real, always-resolvable application id
 * alongside its ghost one — those are handled by bestFrameSuggestion below,
 * not here.
 */
const GHOST_REFERENCE_TYPES = new Set([
  'BROKEN_INFORMATION_FLOW_REFERENCE',
  'UNMAPPED_PROCESS_APPLICATION',
])

/**
 * Which frame(s) a given entity id could plausibly appear in, by its stable
 * id prefix (APP-/BP-/BPM-/FLOW-/IF-, per the dataset's own surrogate-id
 * convention). "application" is the only guaranteed one — GraphBuilderService
 * adds every valid Application as a vertex unconditionally, regardless of
 * whether it has any relationships/interfaces/flows/process mappings — the
 * others are good-but-not-certain heuristics, same standard this toast has
 * always used. The "domain" frame is never returned: no finding type's
 * relatedEntityIds ever names a domain (domain nodes are synthetic
 * aggregates), so it's never a useful place to send someone.
 */
function framesSupporting(id) {
  if (id.startsWith('APP-')) return ['application', 'process', 'infoflow']
  if (id.startsWith('BP-') || id.startsWith('BPM-')) return ['process']
  if (id.startsWith('FLOW-') || id.startsWith('IF-')) return ['infoflow']
  return []
}

/**
 * For a "nothing to highlight in the current frame" miss, the best other
 * frame to suggest — works symmetrically from any current frame (including
 * Domain, which never resolves any entity id) to any target frame. Returns
 * null when nothing else would help either (a true ghost reference).
 */
function bestFrameSuggestion(type, findings, currentFrame) {
  const candidates = new Set()
  for (const finding of findings) {
    if (finding?.type !== type) continue
    for (const id of finding.relatedEntityIds ?? []) {
      for (const frameName of framesSupporting(id)) candidates.add(frameName)
    }
  }
  candidates.delete(currentFrame)
  for (const preferred of ['application', 'process', 'infoflow']) {
    if (candidates.has(preferred)) return preferred
  }
  return null
}

function uploadToast(report) {
  const issues = report?.issues ?? []
  const errors = issues.filter((issue) => issue.severity === 'ERROR').length
  const warnings = issues.filter((issue) => issue.severity === 'WARNING').length

  return {
    variant: errors > 0 ? 'error' : issues.length > 0 ? 'warning' : 'success',
    message:
      `Dataset loaded — ${issues.length} validation issue${issues.length === 1 ? '' : 's'}` +
      (issues.length > 0 ? ` (${errors} error${errors === 1 ? '' : 's'}, ${warnings} warning${warnings === 1 ? '' : 's'}).` : '.'),
  }
}

function Workspace({ initialReport }) {
  const [frame, setFrame] = useState('application')
  const [refreshKey, setRefreshKey] = useState(0)
  const [controlsOpen, setControlsOpen] = useState(false)
  const [insightsOpen, setInsightsOpen] = useState(true)
  const [activeIssueType, setActiveIssueType] = useState(null)
  const [filters, setFilters] = useState(EMPTY_FILTERS)
  // Matrix dimensions are resolved server-side; the rest stay client-side.
  const serverFilters = useMemo(() => toServerFilters(filters, frame), [filters, frame])
  const { elements, loading, error: graphError } = useGraphData(frame, refreshKey, serverFilters)
  const { findings, loading: findingsLoading, error: findingsError } = useInsights(refreshKey)
  const { summary, loading: summaryLoading, error: summaryError } = useSummary(refreshKey)
  const { comparison: gapComparison, loading: gapComparisonLoading } = useGapComparison(refreshKey)
  const [selectedNode, setSelectedNode] = useState(null)
  // Controls the NodePopupDialog shown when a node is tapped on the graph.
  const [nodePopupOpen, setNodePopupOpen] = useState(false)
  const [toast, setToast] = useState(() => uploadToast(initialReport))
  // Holds the live Cytoscape instance for client-side PNG export.
  const cyRef = useRef(null)

  // Map node ids -> issue-ring classes (gap / eol / spof) from the findings.
  const nodeClasses = useMemo(
    () => nodeClassesFromFindings(findings),
    [findings],
  )

  const focusedIssueNodeIds = useMemo(() => {
    if (!activeIssueType) return null
    return findings
      .filter((finding) => finding?.type === activeIssueType)
      .flatMap((finding) => finding.relatedEntityIds ?? [])
  }, [activeIssueType, findings])

  const filterOptions = useMemo(() => {
    const options = new Map()

    for (const element of elements) {
      const data = element.data ?? {}
      if (data.source && data.target) continue

      if (frame === 'application' && data.businessDomain) {
        options.set(data.businessDomain, data.businessDomain)
      } else if (frame === 'domain' && data.type === 'domain') {
        options.set(data.id, data.label)
      } else if (frame === 'process' && data.type === 'process') {
        options.set(data.id, data.label)
      } else if (frame === 'infoflow' && data.type === 'informationObject') {
        options.set(data.id, data.label)
      }
    }

    return [...options].map(([value, label]) => ({ value, label }))
      .sort((first, second) => first.label.localeCompare(second.label))
  }, [elements, frame])

  /**
   * The selectable values for each server-known filter dimension (business
   * criticality, lifecycle status, hosting, classification, plus the legacy
   * matrix dimensions). Comes from GET /api/filters, which derives them from
   * the full model — so the option lists never shrink as filters narrow the
   * graph. `hasMatrixData` only reflects whether any list came back
   * non-empty; it is intentionally NOT wired to FrameTabs (the matrix frames
   * it used to gate no longer exist on this backend at all).
   */
  const { options: serverOptions } = useFilterOptions(refreshKey)

  const handleFrameChange = (nextFrame) => {
    setFilters(EMPTY_FILTERS)
    setFrame(nextFrame)
  }

  // After a successful upload, refresh all data and toast the validation result.
  const handleUploaded = (report) => {
    setSelectedNode(null)
    setNodePopupOpen(false)
    setActiveIssueType(null)
    setFilters(EMPTY_FILTERS)
    setRefreshKey((k) => k + 1)
    setToast(uploadToast(report))
  }

  const handleUploadError = () => {
    setToast({ variant: 'error', message: 'Upload failed. Please check the file and try again.' })
  }

  const handleExportError = () => {
    setToast({ variant: 'error', message: 'Export failed. Please try again.' })
  }

  // Called by GraphCanvas on node tap/search-select (node data) or background
  // tap (null). Opens the popup dialog whenever a node becomes selected, and
  // closes it when the selection is cleared — without touching the existing
  // highlight/blast-radius logic that already lives inside GraphCanvas.
  const handleNodeSelect = (node) => {
    setSelectedNode(node)
    setNodePopupOpen(Boolean(node))
  }

  const closeNodePopup = () => {
    setNodePopupOpen(false)
  }

  useEffect(() => {
    const handleKeyDown = (event) => {
      if (event.key === 'Escape') {
        setControlsOpen(false)
        setInsightsOpen(false)
      }
    }

    window.addEventListener('keydown', handleKeyDown)
    return () => window.removeEventListener('keydown', handleKeyDown)
  }, [])

  const toggleControls = () => {
    setControlsOpen((open) => !open)
  }

  return (
    <div className="app">
      <header className="app-header">
        <h1 className="app-title">
          AI-Powered Enterprise Context Diagram Generator & Insight Engine
        </h1>
        <ExportButton getCy={() => cyRef.current} onError={handleExportError} />
      </header>

      <div className="app-body">
        <button
          type="button"
          className={`panel-toggle panel-toggle-left${controlsOpen ? ' is-open' : ''}`}
          onClick={toggleControls}
          aria-label={controlsOpen ? 'Close filters and controls' : 'Open filters and controls'}
          aria-controls="control-panel"
          aria-expanded={controlsOpen}
          title={controlsOpen ? 'Close controls' : 'Filters and controls'}
        >
          {controlsOpen ? <X size={20} /> : <ListFilter size={20} />}
        </button>

        <aside
          id="control-panel"
          className={`control-panel panel-drawer${controlsOpen ? ' is-open' : ''}`}
          aria-label="Controls"
          aria-hidden={!controlsOpen}
          inert={!controlsOpen ? '' : undefined}
        >
          <h2 className="panel-heading">Controls</h2>
          <FilterPanel
            filters={filters}
            onChange={setFilters}
            onReset={() => {
              setFilters(EMPTY_FILTERS)
              // Clearing filters alone doesn't undo a manual pan/zoom — also
              // re-fit the camera to whatever's currently visible.
              fitReadable(cyRef.current)
            }}
            frame={frame}
            options={filterOptions}
            serverOptions={serverOptions}
          />
        </aside>

        <main className="graph-canvas" aria-label="Graph canvas">
          <div className="frame-tabs-bar">
            <FrameTabs
              activeFrame={frame}
              onFrameChange={handleFrameChange}
            />
          </div>
          <DashboardCards
            findings={findings}
            loading={findingsLoading}
            error={findingsError}
            activeType={activeIssueType}
            onSelect={(type) => setActiveIssueType((current) => current === type ? null : type)}
          />
          <div className="graph-canvas-body">
            <GraphCanvas
              elements={elements}
              frame={frame}
              loading={loading}
              error={graphError}
              nodeClasses={nodeClasses}
              focusedNodeIds={focusedIssueNodeIds}
              filters={filters}
              onNodeSelect={handleNodeSelect}
              onFocusMiss={() => {
                const doesNotExistMessage =
                  "This finding's record couldn't be shown on the graph — it references an entity that doesn't exist in the dataset."
                let message = doesNotExistMessage
                if (!GHOST_REFERENCE_TYPES.has(activeIssueType)) {
                  const suggestion = bestFrameSuggestion(activeIssueType, findings, frame)
                  if (suggestion) {
                    message = `This finding isn't part of the ${FRAME_LABELS[frame] ?? frame} view — switch to ${FRAME_LABELS[suggestion] ?? suggestion} to see it highlighted.`
                  }
                }
                setToast({ variant: 'warning', message })
              }}
              onReady={(cy) => {
                cyRef.current = cy
              }}
            />
          </div>
        </main>

        {insightsOpen ? (
          <aside
            id="insights-panel"
            className="insights-panel"
            aria-label="AI insights"
          >
            <div className="insights-panel-header">
              <h2 className="panel-heading">Insights</h2>
              <button
                type="button"
                className="insights-close"
                onClick={() => setInsightsOpen(false)}
                aria-label="Close insights panel"
                title="Close insights"
              >
                <X size={17} />
              </button>
            </div>
            <InsightsPanel
              summary={summary}
              summaryLoading={summaryLoading}
              summaryError={summaryError}
              gapComparison={gapComparison}
              gapComparisonLoading={gapComparisonLoading}
              frame={frame}
              elements={elements}
              findings={findings}
              findingsLoading={findingsLoading}
              findingsError={findingsError}
              activeType={activeIssueType}
              onIssueSelect={setActiveIssueType}
            />
            {/* <h3 className="insights-label">Selection</h3>
            <NodeDetail
              node={selectedNode}
              issueClasses={selectedNode ? nodeClasses[selectedNode.id] ?? '' : ''}
            /> */}
          </aside>
        ) : (
          <button
            type="button"
            className="insights-reopen"
            onClick={() => setInsightsOpen(true)}
            aria-label="Open insights panel"
            aria-controls="insights-panel"
            aria-expanded="false"
            title="Open insights"
          >
            <PanelRightOpen size={19} />
          </button>
        )}
      </div>

      <NodePopupDialog
        open={nodePopupOpen}
        node={selectedNode}
        issueClasses={selectedNode ? nodeClasses[selectedNode.id] ?? '' : ''}
        findings={findings}
        onClose={closeNodePopup}
      />

      {toast && (
        <Toast
          message={toast.message}
          variant={toast.variant}
          onClose={() => setToast(null)}
        />
      )}
    </div>
  )
}

function App() {
  const [initialReport, setInitialReport] = useState(null)

  if (!initialReport) {
    return <UploadPage onUploaded={setInitialReport} />
  }

  return <Workspace initialReport={initialReport} />
}

export default App
