import { useEffect, useRef } from 'react'
import cytoscape from 'cytoscape'
import fcose from 'cytoscape-fcose'
import cytoscapeSvg from 'cytoscape-svg'
import { getNodeImpact } from '../services/api'
import { ISSUE_CLASSES } from '../services/insightAdapter'
import { fitReadable } from '../services/graphViewport'
import GraphLegend from './GraphLegend'
import './GraphCanvas.css'

cytoscape.use(fcose)
cytoscape.use(cytoscapeSvg)

/**
 * Cytoscape stylesheet ported from the vanilla prototype (baseStyle()).
 * Nodes are rounded rectangles colored by domain; edges are directed with
 * triangle arrowheads. Extra selectors cover dim/highlight/issue rings and
 * the domain / process frame node variants.
 */
function baseStyle() {
  return [
    {
      selector: 'node',
      style: {
        'background-color': 'data(color)',
        label: 'data(label)',
        color: '#fff',
        'text-valign': 'center',
        'text-halign': 'center',
        'font-size': '14px',
        'font-weight': '700',
        'text-wrap': 'wrap',
        'text-max-width': '132px',
        width: '148px',
        height: '66px',
        shape: 'round-rectangle',
        'border-width': 2,
        'border-color': '#fff',
        'text-outline-width': 0,
      },
    },
    { selector: 'node[sub]', style: { 'font-size': '11px' } },
    // Placeholder for an id that records point at but which has no row of its
    // own — an application or a business process. Declared BEFORE the
    // issue-ring classes so that a node also carrying a broken-reference
    // finding still gets its red ring.
    {
      selector: 'node[type="applicationGhost"], node[type="processGhost"]',
      style: {
        shape: 'round-rectangle',
        'border-width': 3,
        'border-color': '#68767e',
        'border-style': 'dashed',
        'font-size': '12px',
        'font-style': 'italic',
      },
    },
    { selector: '.dim', style: { opacity: 0.15 } },
    { selector: '.issue-focus-dim', style: { opacity: 0.1 } },
    { selector: '.filter-search-dim', style: { opacity: 0.08 } },
    {
      selector: 'node.filter-search-active',
      style: { opacity: 1, 'border-width': 5, 'border-color': '#087f78', 'z-index': 100 },
    },
    {
      selector: 'edge.filter-search-active',
      style: {
        opacity: 1,
        width: 4,
        'line-color': '#087f78',
        'target-arrow-color': '#087f78',
        'z-index': 99,
      },
    },
    // Issue-ring classes, declared LOW-priority-first: Cytoscape resolves shared
    // border properties by last-matching-rule-wins, so when a node carries 2+
    // finding classes at once (e.g. a hub that's also missing an owner), the
    // rule declared latest here wins the ring — kept intentionally ordered so
    // the highest-severity applicable finding is always the one shown, not
    // whichever class happened to be declared last. See the `multi-issue`
    // rule below for how a second/third simultaneous issue stays visible too.
    { selector: '.gap', style: { 'border-color': '#df6249', 'border-width': 4 } },
    {
      selector: '.eol',
      style: { 'border-color': '#c2415d', 'border-width': 4, 'border-style': 'dashed' },
    },
    {
      selector: 'node.duplicate',
      style: { 'border-color': '#ea580c', 'border-width': 4, 'border-style': 'dashed' },
    },
    {
      selector: 'node.orphan',
      style: { 'border-color': '#64748b', 'border-width': 4, 'border-style': 'double' },
    },
    {
      selector: 'node.lifecycle-risk',
      style: { 'border-color': '#f59e0b', 'border-width': 4, 'border-style': 'solid' },
    },
    {
      selector: 'node.broken-ref',
      style: { 'border-color': '#dc2626', 'border-width': 4, 'border-style': 'solid' },
    },
    {
      selector: 'node.unmapped',
      style: { 'border-color': '#94a3b8', 'border-width': 4, 'border-style': 'dotted' },
    },
    {
      selector: 'node.circular',
      style: { 'border-color': '#7c3aed', 'border-width': 5, 'border-style': 'double' },
    },
    {
      selector: 'node.orphan-interface',
      style: { 'border-color': '#68767e', 'border-width': 4, 'border-style': 'dotted' },
    },
    {
      selector: 'node.deprecated-interface',
      style: { 'border-color': '#8b5cf6', 'border-width': 4, 'border-style': 'dotted' },
    },
    {
      selector: '.spof',
      style: { 'border-width': 4, 'border-color': '#2563a6', 'border-style': 'dashed' },
    },
    {
      selector: 'node.sensitive-flow',
      style: { 'border-color': '#be123c', 'border-width': 5, 'border-style': 'double' },
    },
    // A node carrying 2+ of the classes above at once: thicken the ring so the
    // presence of multiple issues is visible even though only one ring color
    // shows (see the ordering comment above) — the full list is always
    // available in NodeDetail. Declared last so it always wins the width.
    { selector: 'node.multi-issue', style: { 'border-width': 7 } },
    // Base edge FIRST, then the type-specific overrides. Cytoscape resolves
    // equal specificity by last-matching-rule-wins, so a bare `edge` rule
    // declared after these would silently swallow any property it shares with
    // them — the same hazard documented for the issue rings above.
    {
      selector: 'edge',
      style: {
        width: 2,
        'line-color': '#9aa7a5',
        'target-arrow-color': '#9aa7a5',
        'target-arrow-shape': 'triangle',
        'curve-style': 'bezier',
        'arrow-scale': 1,
      },
    },
    {
      selector: 'edge[type = "DEPENDS_ON"]',
      style: { 'line-style': 'solid' },
    },
    {
      selector: 'edge[type = "USES"]',
      style: { 'line-style': 'dashed' },
    },
    // Interface and information-flow edges — separate lines from any
    // dependency edge between the same two applications, not merged into it.
    // Coloured rather than grey so they stand out from a plain dependency.
    {
      selector: 'edge[kind = "interface"]',
      style: { 'line-style': 'solid', 'line-color': '#3f8fa8', 'target-arrow-color': '#3f8fa8' },
    },
    {
      selector: 'edge[kind = "flow"]',
      style: { 'line-style': 'dashed', 'line-color': '#8a63c7', 'target-arrow-color': '#8a63c7' },
    },
    {
      selector: 'edge[label]',
      style: {
        label: 'data(label)',
        color: '#31464c',
        'font-size': '11px',
        'font-weight': '600',
        'text-wrap': 'wrap',
        'text-max-width': '120px',
        'text-rotation': 'autorotate',
        'text-margin-y': -8,
        'text-background-color': '#ffffff',
        'text-background-opacity': 0.92,
        'text-background-padding': '4px',
        'text-border-color': '#d5e1de',
        'text-border-width': 1,
        'text-border-opacity': 1,
        'text-events': 'yes',
      },
    },
    {
      selector: 'edge.orphan-interface',
      style: {
        width: 4,
        'line-color': '#68767e',
        'target-arrow-color': '#68767e',
        'line-style': 'dotted',
        'z-index': 98,
      },
    },
    { selector: 'edge.dim', style: { opacity: 0.08 } },
    // Impact / blast-radius highlighting (from getNodeImpact):
    // incoming provider edges (upstream -> origin) vs outgoing consumer edges.
    {
      selector: 'edge.impact-in',
      style: {
        width: 4,
        'line-color': '#2f77b4',
        'target-arrow-color': '#2f77b4',
        'z-index': 99,
      },
    },
    {
      selector: 'edge.impact-out',
      style: {
        width: 4,
        'line-color': '#e07b39',
        'target-arrow-color': '#e07b39',
        'z-index': 99,
      },
    },
    {
      selector: 'node.impact-origin',
      style: { 'border-width': 5, 'border-color': '#0E4A47' },
    },
    {
      selector: 'node[type="process"]',
      style: {
        'background-color': '#0E4A47',
        shape: 'round-rectangle',
        width: '140px',
        height: '48px',
        'font-size': '13px',
      },
    },
    {
      selector: 'node[type="domain"]',
      style: {
        width: '150px',
        height: '76px',
        'font-size': '14px',
        'font-weight': '700',
      },
    },
  ]
}

/**
 * Layout configuration per frame, ported from the prototype (layoutFor()).
 * @param {string} frame - The active observation frame.
 * @param {Array<object>} [elements] - The elements about to be laid out. Only
 *   consulted for the domain frame: a domain ANCHOR (see AnchorPicker) scopes
 *   to that domain's individual applications rather than domain-to-domain
 *   bubbles, and the circle layout below is tuned for a dozen large bubbles,
 *   not a few dozen application nodes — using it there was spreading nodes to
 *   the far corners of the canvas. Detect which shape is actually present
 *   rather than trusting the frame name alone.
 */
function layoutFor(frame, elements = []) {
  if (frame === 'domain' && elements.some((el) => el.data?.type === 'domain')) {
    return {
      name: 'circle',
      padding: 80,
      spacingFactor: 1.8,
      avoidOverlap: true,
      nodeDimensionsIncludeLabels: true,
    }
  }
  if (frame === 'process') {
    return {
      name: 'breadthfirst',
      directed: true,
      padding: 80,
      spacingFactor: 1.9,
      avoidOverlap: true,
      nodeDimensionsIncludeLabels: true,
    }
  }
  return {
    name: 'fcose',
    quality: 'proof',
    randomize: true,
    fit: false,
    padding: 80,
    nodeDimensionsIncludeLabels: true,
    packComponents: true,
    nodeSeparation: 90,
    nodeRepulsion: () => 9000,
    idealEdgeLength: () => 220,
    edgeElasticity: () => 0.35,
    nestingFactor: 0.1,
    numIter: 2500,
    tile: true,
    tilingPaddingVertical: 80,
    tilingPaddingHorizontal: 80,
    gravity: 0.2,
    animate: false,
  }
}

/** CSS classes used for the transient impact/selection highlight. */
const HIGHLIGHT_CLASSES = 'dim impact-in impact-out impact-origin'

/** Removes any active blast-radius highlight from the graph. */
function clearHighlight(cy) {
  cy.elements().removeClass(HIGHLIGHT_CLASSES)
}

/**
 * Highlights the blast radius returned by getNodeImpact(): dims everything,
 * un-dims affected nodes, and colors incoming (provider) edges differently
 * from outgoing (consumer) edges relative to the origin.
 *
 * @param {object} cy - The Cytoscape instance.
 * @param {string} originId - The tapped node id.
 * @param {object} result - The ImpactAnalysisResult from the backend.
 */
function applyImpactHighlight(cy, originId, result) {
  const affected = result?.affected ?? []
  const upstream = new Set(result?.upstream ?? [])
  const downstream = new Set(result?.downstream ?? [])

  cy.batch(() => {
    cy.elements().addClass('dim')

    cy.getElementById(originId).removeClass('dim').addClass('impact-origin')
    for (const id of affected) {
      cy.getElementById(id).removeClass('dim')
    }

    for (const edge of result?.edges ?? []) {
      const el = cy.getElementById(edge.id)
      if (el.empty()) continue
      el.removeClass('dim')
      // Incoming: provider feeds the origin (or the upstream chain).
      const incoming = upstream.has(edge.source) && (edge.target === originId || upstream.has(edge.target))
      // Outgoing: origin feeds a consumer (or the downstream chain).
      const outgoing = downstream.has(edge.target) && (edge.source === originId || downstream.has(edge.source))
      el.addClass(incoming ? 'impact-in' : outgoing ? 'impact-out' : 'impact-out')
    }
  })
}

/**
/** Fallback highlight for frames whose node ids are not applications (domain /
 * process). Dims everything except the tapped node's closed neighborhood.
 */
function applyNeighborhoodHighlight(cy, node) {
  cy.batch(() => {
    cy.elements().addClass('dim')
    node.closedNeighborhood().removeClass('dim')
    node.addClass('impact-origin')
    node.connectedEdges().removeClass('dim').addClass('impact-out')
  })
}

/**
 * Applies frame-aware dropdown and issue filtering without changing the
 * graph layout. Search highlighting is handled separately so it can use the
 * same impact-analysis path as a node click.
 *
 * @param {object} cy - The Cytoscape instance.
 * @param {string} frame - The active frame.
 * @param {object} [filters] - { domain, lifecycle, gapOnly, eolOnly, search,
 *   businessCriticality, lifecycleStatuses, hosting, classification }. The
 *   last four are arrays (multi-select checkbox groups); empty = no restriction.
 * @param {Record<string, string>} [nodeClasses] - Issue-ring classes per node
 *   id (from nodeClassesFromFindings), used by `gapOnly` since ownership is no
 *   longer a plain node-data attribute.
 */
function applyFilters(cy, frame, filters = {}, nodeClasses = {}) {
  const {
    domain, lifecycle, gapOnly, eolOnly,
    businessCriticality = [], lifecycleStatuses = [], hosting = [], classification = [],
  } = filters
  const hasActiveFilter = Object.entries(filters).some(([key, value]) => {
    if (key === 'search') return false
    if (Array.isArray(value)) return value.length > 0
    return typeof value === 'string' ? value.trim() !== '' : value === true
  })
  const matchedNodeIds = new Set()

  cy.batch(() => {
    cy.elements().style('display', 'element')

    cy.nodes().forEach((n) => {
      const d = n.data()
      let show = true
      if (frame === 'application') {
        if (domain && d.businessDomain !== domain) show = false
        if (lifecycle && d.lifecycleStatus !== lifecycle) show = false
        if (gapOnly && !(nodeClasses[d.id] ?? '').includes('gap')) show = false
        if (eolOnly && d.lifecycleStatus !== 'END_OF_LIFE') show = false
        if (businessCriticality.length && !businessCriticality.includes(d.businessCriticality)) show = false
        if (lifecycleStatuses.length && !lifecycleStatuses.includes(d.lifecycleStatus)) show = false
        if (hosting.length && !hosting.includes(d.hosting)) show = false
      } else if (frame === 'infoflow') {
        if (classification.length && d.type === 'informationObject'
            && !classification.includes(d.classification)) {
          show = false
        }
        if (domain && d.id !== domain) show = false
      } else if (domain && d.id !== domain) {
        show = false
      }
      if (show) matchedNodeIds.add(n.id())
    })

    const relatedNodeIds = new Set(matchedNodeIds)
    if (hasActiveFilter) {
      for (const id of matchedNodeIds) {
        cy.getElementById(id).neighborhood('node').forEach((node) => relatedNodeIds.add(node.id()))
      }
    }

    cy.nodes().forEach((node) => {
      node.style('display', relatedNodeIds.has(node.id()) ? 'element' : 'none')
    })

    cy.edges().forEach((e) => {
      const visible = hasActiveFilter
        ? matchedNodeIds.has(e.data('source')) || matchedNodeIds.has(e.data('target'))
        : true
      e.style('display', visible ? 'element' : 'none')
    })
  })

  return { hasActiveFilter, matchedNodeIds }
}

/** Highlights matched nodes and every visible relation connected to them. */
function applyActiveFilterHighlight(cy, hasActiveFilter, matchedNodeIds) {
  cy.elements().removeClass('filter-search-active filter-search-dim')
  if (!hasActiveFilter) return

  cy.nodes(':visible').addClass('filter-search-dim')
  for (const id of matchedNodeIds) {
    const node = cy.getElementById(id)
    node.removeClass('filter-search-dim').addClass('filter-search-active')
    node.connectedEdges(':visible').addClass('filter-search-active')
    node.neighborhood('node:visible').removeClass('filter-search-dim')
  }
}

/**
 * Renders an interactive Cytoscape graph.
 *
 * @param {object} props
 * @param {Array<object>} props.elements - Cytoscape elements (nodes + edges).
 * @param {string} props.frame - The active frame, used to pick the layout.
 * @param {boolean} [props.loading] - Whether a graph fetch is in progress.
 * @param {unknown} [props.error] - Error from the graph fetch, if any.
 * @param {Record<string, string>} [props.nodeClasses] - Map of node id -> issue
 *   class string (e.g. "gap eol") for highlighting ownership gaps, EOL and SPOF nodes.
 * @param {Array<string> | null} [props.focusedNodeIds] - Node ids to isolate when
 *   an issue category is selected, or null to show the full graph.
 * @param {object} [props.filters] - Client-side filter state
 *   ({ domain, lifecycle, gapOnly, eolOnly, search, businessCriticality,
 *   lifecycleStatuses, hosting, classification }) applied to the active frame.
 * @param {(node: object | null) => void} [props.onNodeSelect] - Called with the
 *   selected node's data on tap, or null when the selection is cleared.
 * @param {(edge: object | null) => void} [props.onEdgeSelect] - Called with the
 *   selected edge's data (plus resolved endpoint labels) on tap, or null when
 *   the selection is cleared. Selecting a node clears any edge selection and
 *   vice versa, so only one popup is ever open at a time.
 * @param {(cy: object | null) => void} [props.onReady] - Called with the Cytoscape
 *   instance once initialized (and null on unmount), e.g. for PNG export.
 * @param {() => void} [props.onFocusMiss] - Called when focusedNodeIds is set
 *   but none of the ids resolve to any element in the current frame (e.g. a
 *   broken-reference finding whose target record doesn't exist anywhere).
 */
function GraphCanvas({
  elements = [],
  frame = 'application',
  loading = false,
  error = null,
  nodeClasses = {},
  focusedNodeIds = null,
  filters,
  onNodeSelect,
  onEdgeSelect,
  onReady,
  onFocusMiss,
}) {
  const containerRef = useRef(null)
  const cyRef = useRef(null)
  // Guards against stale async getNodeImpact() responses.
  const impactTokenRef = useRef(0)
  // Keep the latest callbacks/frame without forcing the graph to re-init.
  const handlersRef = useRef({ onNodeSelect, onEdgeSelect, frame, onReady, onFocusMiss })
  handlersRef.current = { onNodeSelect, onEdgeSelect, frame, onReady, onFocusMiss }

  // Initialize Cytoscape once, tear it down on unmount.
  useEffect(() => {
    const cy = cytoscape({
      container: containerRef.current,
      elements: [],
      style: baseStyle(),
      wheelSensitivity: 0.2,
      minZoom: 0.08,
      maxZoom: 2.5,
    })
    cyRef.current = cy
    handlersRef.current.onReady?.(cy)

    cy.on('tap', 'node', (e) => {
      const node = e.target
      const id = node.id()
      const { onNodeSelect: onSelect, onEdgeSelect: onEdge, frame: activeFrame } = handlersRef.current

      // Emit the node data enriched with its connection count (graph degree)
      // and, for the application frame, a per-connection breakdown (which
      // application, via what kind of edge, with its key business metadata)
      // rather than just the bare count.
      const connectionsDetail = node.connectedEdges().map((connectedEdge) => {
        const data = connectedEdge.data()
        const outgoing = data.source === id
        const otherEnd = outgoing ? connectedEdge.target() : connectedEdge.source()
        return {
          ...data,
          direction: outgoing ? 'outgoing' : 'incoming',
          otherId: otherEnd.id(),
          otherLabel: otherEnd.data('label'),
        }
      })
      onSelect?.({ ...node.data(), connections: node.degree(false), connectionsDetail })
      onEdge?.(null)
      clearHighlight(cy)

      // Applications resolve a real blast radius from the backend; other frames
      // (domain / process) fall back to a local neighborhood highlight.
      if (activeFrame === 'application') {
        const token = (impactTokenRef.current += 1)
        getNodeImpact(id)
          .then((result) => {
            if (token !== impactTokenRef.current || cyRef.current !== cy) return
            applyImpactHighlight(cy, id, result)
          })
          .catch(() => {
            if (token === impactTokenRef.current) applyNeighborhoodHighlight(cy, node)
          })
      } else {
        applyNeighborhoodHighlight(cy, node)
      }
    })

    cy.on('tap', 'edge', (e) => {
      const edge = e.target
      const { onNodeSelect: onSelect, onEdgeSelect: onEdge } = handlersRef.current
      onEdge?.({
        ...edge.data(),
        sourceLabel: edge.source().data('label'),
        targetLabel: edge.target().data('label'),
      })
      onSelect?.(null)
    })

    cy.on('tap', (e) => {
      if (e.target !== cy) return
      // Background click: invalidate any pending impact request and reset.
      impactTokenRef.current += 1
      clearHighlight(cy)
      cy.$(':selected').unselect()
      handlersRef.current.onNodeSelect?.(null)
      handlersRef.current.onEdgeSelect?.(null)
    })

    return () => {
      handlersRef.current.onReady?.(null)
      cy.destroy()
      cyRef.current = null
    }
  }, [])

  // Re-render elements and re-run the layout whenever elements or frame change.
  useEffect(() => {
    const cy = cyRef.current
    if (!cy) return

    // A new projection clears any active selection/highlight.
    impactTokenRef.current += 1
    handlersRef.current.onNodeSelect?.(null)
    handlersRef.current.onEdgeSelect?.(null)

    cy.batch(() => {
      cy.elements().remove()
      cy.add(elements)
    })
    const layout = cy.layout(layoutFor(frame, elements))
    layout.one('layoutstop', () => fitReadable(cy))
    layout.run()
  }, [elements, frame])

  // Resize Cytoscape with its container without overriding the user's viewport.
  useEffect(() => {
    const container = containerRef.current
    const cy = cyRef.current
    if (!container || !cy || typeof ResizeObserver === 'undefined') return undefined

    const observer = new ResizeObserver(() => {
      cy.resize()
    })
    observer.observe(container)

    return () => observer.disconnect()
  }, [])

  // Apply category-colored issue rings to finding entities and orphan endpoints.
  // Runs after the elements effect above, and again when the map changes.
  useEffect(() => {
    const cy = cyRef.current
    if (!cy) return

    cy.batch(() => {
      // Clear any stale rings first.
      cy.elements().removeClass(ISSUE_CLASSES.join(' ')).removeClass('multi-issue')
      for (const [id, classes] of Object.entries(nodeClasses)) {
        if (!classes) continue
        const element = cy.getElementById(id)
        element.addClass(classes)
        if (element.isEdge() && classes.includes('orphan-interface')) {
          element.connectedNodes().addClass('orphan-interface')
        }
        // A node can carry several issue rings at once (e.g. a hub that's also
        // missing an owner); the stylesheet's cascade only ever shows one ring
        // color/style (the highest-severity one wins — see baseStyle()'s
        // declaration order). Thickening the ring when 2+ apply keeps that
        // second (and third...) issue from being silently invisible — the full
        // list is always one click away in NodeDetail's badges/source records.
        if (classes.trim().split(/\s+/).length > 1) {
          element.addClass('multi-issue')
        }
      }
    })
  }, [elements, nodeClasses])

  // Focus the graph on nodes associated with the selected issue category.
  useEffect(() => {
    const cy = cyRef.current
    if (!cy) return

    cy.elements().removeClass('issue-focus-dim')
    if (!focusedNodeIds) return

    // A finding's relatedEntityIds are raw domain-model ids (e.g. an
    // information-flow id like "FLOW-0020" or an interface id like
    // "IF-0020"). In the application frame those ARE the element ids (each
    // relationship/interface/flow record is its own edge), so the direct
    // lookup below usually resolves immediately. It isn't always a literal
    // element id though: the info-flow view stores its edges as
    // "<flowId>:produces" / "<flowId>:consumes" (with the flow/interface id
    // only inside edge data). So fall back to matching by id-prefix, by the
    // flowId/interfaceId data fields, and by `memberIds` (still present on
    // every application-frame edge, just single-membered now) before giving
    // up on an id.
    const seen = new Set()
    const focusedElements = []
    for (const id of focusedNodeIds) {
      let matches = cy.getElementById(id)
      if (matches.empty()) {
        matches = cy.filter((element) => {
          const data = element.data()
          return element.id().startsWith(`${id}:`)
            || data.flowId === id
            || data.interfaceId === id
            || (Array.isArray(data.memberIds) && data.memberIds.includes(id))
        })
      }
      matches.forEach((element) => {
        if (!seen.has(element.id())) {
          seen.add(element.id())
          focusedElements.push(element)
        }
      })
    }
    if (focusedElements.length === 0) {
      // Genuinely nothing to show — e.g. a broken-reference finding whose
      // target record doesn't exist, so the backend never created a graph
      // element for it in the first place. Surface that instead of silently
      // doing nothing (which looks like the click didn't register).
      handlersRef.current.onFocusMiss?.()
      return
    }

    cy.elements().addClass('issue-focus-dim')
    for (const element of focusedElements) {
      element.removeClass('issue-focus-dim')
      if (element.isNode()) {
        element.connectedEdges().removeClass('issue-focus-dim')
        element.neighborhood('node').removeClass('issue-focus-dim')
      } else {
        element.connectedNodes().removeClass('issue-focus-dim')
      }
    }
  }, [elements, focusedNodeIds])

  // Apply client-side filters (hide non-matching nodes/edges). Runs after the
  // elements effect, and again when filters or the frame change.
  useEffect(() => {
    const cy = cyRef.current
    if (!cy) return
    const { hasActiveFilter, matchedNodeIds } = applyFilters(cy, frame, filters, nodeClasses)
    applyActiveFilterHighlight(cy, hasActiveFilter, matchedNodeIds)
  }, [elements, frame, filters, nodeClasses])

  // Search behaves like selecting the first matching node: applications use
  // backend blast-radius analysis, while aggregate frames use the local
  // closed-neighborhood highlight. A short delay avoids requests per keystroke.
  useEffect(() => {
    const cy = cyRef.current
    if (!cy) return undefined

    const query = (filters?.search ?? '').trim().toLowerCase()
    impactTokenRef.current += 1
    clearHighlight(cy)
    if (!query) return undefined

    const timeout = window.setTimeout(() => {
      const matches = cy.nodes(':visible').filter((node) =>
        (node.data('label') ?? '').toLowerCase().includes(query),
      )
      if (matches.length === 0) return

      const exactMatch = matches.filter((node) =>
        (node.data('label') ?? '').toLowerCase() === query,
      )
      const node = exactMatch.length > 0 ? exactMatch.first() : matches.first()
      const id = node.id()

      if (frame !== 'application') {
        applyNeighborhoodHighlight(cy, node)
        return
      }

      const token = (impactTokenRef.current += 1)
      getNodeImpact(id)
        .then((result) => {
          if (token !== impactTokenRef.current || cyRef.current !== cy) return
          applyImpactHighlight(cy, id, result)
        })
        .catch(() => {
          if (token === impactTokenRef.current) applyNeighborhoodHighlight(cy, node)
        })
    }, 250)

    return () => {
      window.clearTimeout(timeout)
      impactTokenRef.current += 1
    }
  }, [elements, frame, filters?.search])

  return (
    <div className="graph-canvas-wrap">
      <div ref={containerRef} className="graph-canvas-cy" />

      {!loading && !error && elements.length > 0 && <GraphLegend frame={frame} />}

      {loading && (
        <div className="graph-canvas-loading" role="status" aria-live="polite">
          <span className="graph-canvas-spinner" aria-hidden="true" />
          <span>Loading graph…</span>
        </div>
      )}

      {!loading && error && (
        <div className="graph-canvas-state graph-canvas-state--error" role="alert">
          <span className="graph-canvas-state-icon" aria-hidden="true">⚠</span>
          <p className="graph-canvas-state-title">Couldn’t load the graph</p>
          <p className="graph-canvas-state-text">
            Is the backend running at <code>localhost:8080</code>? Try uploading a dataset.
          </p>
        </div>
      )}

      {!loading && !error && elements.length === 0 && (
        <div className="graph-canvas-state" role="status">
          <span className="graph-canvas-state-icon" aria-hidden="true">◍</span>
          <p className="graph-canvas-state-title">Nothing to display</p>
          <p className="graph-canvas-state-text">
            No elements in this frame. Upload a dataset or switch frames.
          </p>
        </div>
      )}
    </div>
  )
}

export default GraphCanvas

