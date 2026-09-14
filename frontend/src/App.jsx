import { useEffect, useMemo, useRef, useState } from 'react'
import { ListFilter, PanelRightOpen, X } from 'lucide-react'
import AnchorPicker from './components/AnchorPicker'
import DashboardCards from './components/DashboardCards'
import EdgePopupDialog from './components/EdgePopupDialog'
import FrameTabs from './components/FrameTabs'
import FilterPanel from './components/FilterPanel'
import ExportButton from './components/ExportButton'
import GraphCanvas from './components/GraphCanvas'
import InsightsPanel from './components/InsightsPanel'
import LoadingScreen from './components/LoadingScreen'
import NodePopupDialog from './components/NodePopupDialog'
import Toast from './components/Toast'
import UploadPage from './components/UploadPage'
import { useGraphData } from './hooks/useGraphData'
import { useFilterOptions } from './hooks/useFilterOptions'
import { useGapComparison } from './hooks/useGapComparison'
import { useInsights } from './hooks/useInsights'
import { useSummary } from './hooks/useSummary'
import { nodeClassesFromFindings } from './services/insightAdapter'
import { fitReadable } from './services/graphViewport'
import { EMPTY_FILTERS, toServerFilters } from './services/filterState'
import './App.css'

const FRAME_LABELS = {
  application: 'Application',
  domain: 'Domain',
  process: 'Process',
  infoflow: 'Information Flow',
}

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
  // No UI path re-sets this today (there's no re-upload affordance once
  // Workspace is mounted — see App()'s single initialReport gate below), so
  // it's a stable 0 for the lifetime of a session; kept as the refetch key
  // every data-fetching hook below expects rather than removing it outright.
  const [refreshKey] = useState(0)
  const [controlsOpen, setControlsOpen] = useState(false)
  const [insightsOpen, setInsightsOpen] = useState(true)
  const [activeIssueType, setActiveIssueType] = useState(null)
  const [filters, setFilters] = useState(EMPTY_FILTERS)
  // Matrix dimensions are resolved server-side; the rest stay client-side.
  const serverFilters = useMemo(() => toServerFilters(filters, frame), [filters, frame])

  // The anchor a user has picked to focus the diagram on (F2/F3: a genuinely
  // scoped context diagram, not the whole frame with elements dimmed) — see
  // AnchorPicker. Frame-specific, so it resets whenever the frame changes.
  const [anchorId, setAnchorId] = useState(null)
  const [anchorDepth, setAnchorDepth] = useState(1)

  // Always fetch the unscoped frame — it drives both the anchor picker's
  // option list (which must show every possible starting point, not just
  // whatever a current focus already narrowed things down to) and the
  // existing domain-filter dropdown below. When no anchor is picked this is
  // also what's rendered, so focusing/clearing costs at most one extra fetch,
  // not two on the common unfocused path.
  const { elements: fullElements, loading: fullLoading, error: fullGraphError } =
    useGraphData(frame, refreshKey, serverFilters)

  const anchorParams = useMemo(
    () => (anchorId ? { ...serverFilters, anchor: anchorId, depth: anchorDepth } : null),
    [serverFilters, anchorId, anchorDepth],
  )
  const { elements: scopedElements, loading: scopedLoading, error: scopedGraphError } =
    useGraphData(anchorParams ? frame : null, refreshKey, anchorParams ?? {})

  const elements = anchorId ? scopedElements : fullElements
  const loading = anchorId ? scopedLoading : fullLoading
  const graphError = anchorId ? scopedGraphError : fullGraphError

  // Every node in the full (unscoped) frame is a valid anchor — id doubles as
  // the value the backend expects (an application/process/info-object id, or,
  // for the domain frame, the domain node's own id, which IS the raw domain
  // value GraphScopeService.scopeByAttribute matches against).
  //
  // Interfaces are the one anchor type with no node of their own — in the
  // application frame each is its own edge (id = the interface's own id), not
  // a node. Offered here too, application frame only, so "identify consumers
  // of a given interface" has something to pick: GraphScopeService.scope()
  // falls back to matching an edge id and seeds the diagram from both ends.
  const anchorOptions = useMemo(() => {
    const nodeOptions = fullElements
      .filter((el) => !(el.data.source && el.data.target)) // nodes only, no edges
      .map((el) => ({ value: el.data.id, label: el.data.label ?? el.data.id }))
    const interfaceOptions = frame === 'application'
      ? fullElements
          .filter((el) => el.data.source && el.data.target && el.data.type === 'INTERFACE')
          .map((el) => ({ value: el.data.id, label: `${el.data.label ?? el.data.id} (interface)` }))
      : []
    return [...nodeOptions, ...interfaceOptions].sort((a, b) => a.label.localeCompare(b.label))
  }, [fullElements, frame])
  const { findings, loading: findingsLoading, error: findingsError } = useInsights(refreshKey)
  const { summary, loading: summaryLoading, error: summaryError } = useSummary(refreshKey)
  const { comparison: gapComparison, loading: gapComparisonLoading } = useGapComparison(refreshKey)
  const [selectedNode, setSelectedNode] = useState(null)
  // Controls the NodePopupDialog shown when a node is tapped on the graph.
  const [nodePopupOpen, setNodePopupOpen] = useState(false)
  const [selectedEdge, setSelectedEdge] = useState(null)
  // Controls the EdgePopupDialog shown when a relationship/interface/flow
  // edge is tapped. GraphCanvas already keeps node and edge selection
  // mutually exclusive (each tap handler clears the other), so only one of
  // the two popups is ever open at a time.
  const [edgePopupOpen, setEdgePopupOpen] = useState(false)
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
    // Anchor ids are frame-specific (a process id has no meaning in the
    // application frame), so a frame switch always drops back to the full
    // landscape rather than carrying over a now-meaningless anchor.
    setAnchorId(null)
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

  // Called by GraphCanvas on edge tap (edge data) or whenever selection is
  // cleared (node tap, background tap, frame change).
  const handleEdgeSelect = (edge) => {
    setSelectedEdge(edge)
    setEdgePopupOpen(Boolean(edge))
  }

  const closeEdgePopup = () => {
    setEdgePopupOpen(false)
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
        <ExportButton getCy={() => cyRef.current} frame={frame} onError={handleExportError} />
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
            <AnchorPicker
              options={anchorOptions}
              anchorId={anchorId}
              depth={anchorDepth}
              onAnchorChange={setAnchorId}
              onDepthChange={setAnchorDepth}
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
              onEdgeSelect={handleEdgeSelect}
              onFocusMiss={() => {
                // Unresolvable references now render as placeholder nodes in
                // whichever frame declares them, so a miss is almost always a
                // wrong-frame miss rather than a genuinely unshowable record.
                const suggestion = bestFrameSuggestion(activeIssueType, findings, frame)
                setToast({
                  variant: 'warning',
                  message: suggestion
                    ? `This finding isn't part of the ${FRAME_LABELS[frame] ?? frame} view — switch to ${FRAME_LABELS[suggestion] ?? suggestion} to see it highlighted.`
                    : "This finding's record couldn't be shown on the graph — it references an entity that doesn't exist in the dataset.",
                })
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

      <EdgePopupDialog
        open={edgePopupOpen}
        edge={selectedEdge}
        findings={findings}
        onClose={closeEdgePopup}
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
  // A brief cosmetic hand-off between the upload flow and the workspace —
  // not gated on real data readiness (Workspace's own panels each have
  // their own loading state once mounted), just enough to make the jump
  // from the validation summary feel deliberate rather than an instant cut.
  const [transitioning, setTransitioning] = useState(false)

  if (transitioning) {
    return <LoadingScreen onDone={() => setTransitioning(false)} />
  }

  if (!initialReport) {
    return (
      <UploadPage
        onUploaded={(report) => {
          setInitialReport(report)
          setTransitioning(true)
        }}
      />
    )
  }

  return <Workspace initialReport={initialReport} />
}

export default App
