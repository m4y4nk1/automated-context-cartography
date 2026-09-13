/**
 * Maps insight findings (from getInsights()) onto Cytoscape node CSS classes,
 * using the same category colors as the detected-issue navigator.
 */

/** FindingType -> node class name. Types without a ring are omitted. */
const FINDING_CLASS = {
  HUB: 'spof',
  CIRCULAR_DEPENDENCY: 'circular',
  BROKEN_RELATIONSHIP_REFERENCE: 'broken-ref',
  DANGLING_INTERFACE_CONSUMER: 'orphan-interface',
  BROKEN_INFORMATION_FLOW_REFERENCE: 'broken-ref',
  UNMAPPED_PROCESS_APPLICATION: 'unmapped',
  DUPLICATE_APPLICATION: 'duplicate',
  ORPHAN_APPLICATION: 'orphan',
  OWNERSHIP_RECORD_MISSING: 'gap',
  OWNERSHIP_PARTIAL_GAP: 'gap',
  MISSING_OWNER_FIELD: 'gap',
  LIFECYCLE_RISK_CRITICAL_PROCESS: 'lifecycle-risk',
  LIFECYCLE_RISK_EOL_PROVIDER: 'eol',
  LIFECYCLE_INCONSISTENCY: 'lifecycle-risk',
  DEPRECATED_INTERFACE_IN_USE: 'deprecated-interface',
  INTERFACE_WITHOUT_RELATIONSHIP: 'broken-ref',
  SENSITIVE_DATA_INSECURE_FLOW: 'sensitive-flow',
  PHASE_OUT_CRITICAL_PATH: 'lifecycle-risk',
}

/** All issue classes this adapter can assign (used to clear stale rings). */
export const ISSUE_CLASSES = [
  'gap',
  'eol',
  'spof',
  'orphan-interface',
  'circular',
  'unmapped',
  'broken-ref',
  'duplicate',
  'orphan',
  'lifecycle-risk',
  'deprecated-interface',
  'sensitive-flow',
]

/**
 * Builds a map of node id -> space-separated class string from findings.
 * A node can carry several rings (e.g. an unowned EOL-provider app is both
 * gap + eol), and a single finding can now name several related entities
 * (e.g. every application in a detected cycle) via `relatedEntityIds`.
 *
 * @param {Array<{type: string, relatedEntityIds: string[]}>} [findings]
 * @returns {Record<string, string>} e.g. { "APP-08": "eol", "APP-01": "gap spof" }
 */
export function nodeClassesFromFindings(findings) {
  const byId = {}
  for (const finding of findings ?? []) {
    const cls = FINDING_CLASS[finding?.type]
    if (!cls) continue
    for (const entityId of finding.relatedEntityIds ?? []) {
      if (!entityId) continue
      const existing = byId[entityId]
      // Avoid duplicate class tokens.
      if (!existing) {
        byId[entityId] = cls
      } else if (!existing.split(' ').includes(cls)) {
        byId[entityId] = `${existing} ${cls}`
      }
    }
  }
  return byId
}

/**
 * The specific findings touching a given entity id, each tagged with its
 * issue-ring class — lets the UI show the underlying source-record detail
 * (already carried in `finding.message`, e.g. "Relationship 'REL-0065'
 * targets unknown application 'APP-9001'") next to whichever badge it drives,
 * instead of the badge label alone. Reuses the same FindingType -> class
 * mapping as {@link nodeClassesFromFindings} so the two never disagree.
 *
 * @param {Array<{type: string, relatedEntityIds: string[], message: string}>} [findings]
 * @param {string} entityId
 * @returns {Array<{class: string, type: string, message: string}>}
 */
export function findingsForEntity(findings, entityId) {
  if (!entityId) return []
  const matches = []
  for (const finding of findings ?? []) {
    const cls = FINDING_CLASS[finding?.type]
    if (!cls) continue
    if ((finding.relatedEntityIds ?? []).includes(entityId)) {
      matches.push({ class: cls, type: finding.type, message: finding.message })
    }
  }
  return matches
}
