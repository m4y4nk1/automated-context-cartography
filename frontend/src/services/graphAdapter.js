/**
 * Converts the backend GraphDto (nodes/edges) into the element format that
 * Cytoscape expects: `{ data: { ... }, classes?: string }`.
 *
 * The backend does not emit presentation colors, so node colors are derived
 * from the domain here (matching the vanilla prototype's DOMAIN_COLORS).
 */

/** Known domain colors, ported from the prototype. */
const DOMAIN_COLORS = {
  Sales: '#2f77b4',
  Finance: '#2ca089',
  Operations: '#e0a95c',
  Analytics: '#8a63c7',
  Security: '#c0504d',
  Platform: '#4b8b3b',
}

/** Stable fallback palette for domains not present in DOMAIN_COLORS. */
const FALLBACK_PALETTE = [
  '#2f77b4', '#2ca089', '#e0a95c', '#8a63c7',
  '#c0504d', '#4b8b3b', '#4a90a4', '#b4739e',
]

const PROCESS_COLOR = '#0E4A47'
const INFO_OBJECT_COLOR = '#5b6472'

/** Colors for the application-matrix node types. */
const LANDSCAPE_COLOR = '#1f5f8b'
const SITE_COLOR = '#2a7f8f'
const BRAND_COLOR = '#b4739e'
const ACTIVITY_COLOR = '#c98a3c'
const CAPABILITY_COLOR = '#6f5aa8'
const TECHNOLOGY_COLOR = '#4b8b3b'
const INSTANCE_COLOR = '#7a8794'

/**
 * Resolves a stable color for a domain, falling back to a hashed palette color.
 * @param {string} [domain]
 * @returns {string} A hex color.
 */
function colorForDomain(domain) {
  if (!domain) return INFO_OBJECT_COLOR
  if (DOMAIN_COLORS[domain]) return DOMAIN_COLORS[domain]
  let hash = 0
  for (let i = 0; i < domain.length; i += 1) {
    hash = (hash * 31 + domain.codePointAt(i)) >>> 0
  }
  return FALLBACK_PALETTE[hash % FALLBACK_PALETTE.length]
}

/**
 * Picks a node color based on its type/domain.
 * @param {object} node - A GraphNode from the backend.
 */
function colorForNode(node) {
  const data = node.data ?? {}
  switch (node.type) {
    case 'process':
      return PROCESS_COLOR
    case 'informationObject':
      return INFO_OBJECT_COLOR
    case 'landscape':
      return LANDSCAPE_COLOR
    case 'site':
      return SITE_COLOR
    case 'brand':
      return BRAND_COLOR
    case 'activity':
      return ACTIVITY_COLOR
    case 'capability':
      return CAPABILITY_COLOR
    case 'technologyComponent':
      return TECHNOLOGY_COLOR
    case 'applicationInstance':
      return INSTANCE_COLOR
    case 'domain':
      // Domain nodes carry no `domain` attr; derive from the plain name
      // (label is "Name (count)", id is the domain key).
      return colorForDomain(stripCount(node.label) || node.id)
    default:
      return colorForDomain(data.businessDomain)
  }
}

/** Strips a trailing " (n)" suffix from a domain label. */
function stripCount(label) {
  return typeof label === 'string' ? label.replace(/\(\d+\)$/, '').trim() : label
}

/**
 * Converts a GraphDto into an array of Cytoscape elements.
 * @param {{ nodes?: Array<object>, edges?: Array<object> } | null | undefined} graph
 * @returns {Array<object>} Cytoscape elements (nodes first, then edges).
 */
export function toCytoscapeElements(graph) {
  if (!graph) return []

  const nodes = (graph.nodes ?? []).map((node) => ({
    data: {
      ...node.data,
      id: node.id,
      label: node.label,
      type: node.type,
      color: colorForNode(node),
    },
  }))

  const edges = (graph.edges ?? []).map((edge) => ({
    data: {
      ...edge.data,
      id: edge.id,
      source: edge.source,
      target: edge.target,
      label: edge.label,
      type: edge.type,
      kind: edgeKind(edge.type),
    },
  }))

  return [...nodes, ...edges]
}

/**
 * Buckets a backend edge `type` into a coarse kind so consumers (e.g.
 * GraphCanvas) can style edge families without re-deriving this mapping.
 * @param {string} [type]
 */
function edgeKind(type) {
  if (type === 'DEPENDS_ON' || type === 'USES') return 'relationship'
  if (type === 'processMapping') return 'processMapping'
  if (type === 'produces' || type === 'consumes') return 'informationFlow'
  if (type === 'domainFlow') return 'domainFlow'
  return 'other'
}

export { DOMAIN_COLORS }
