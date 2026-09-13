import { useMemo, useState } from 'react'
import { AlertTriangle, ChevronDown, Gauge } from 'lucide-react'
import './ArchitectureHealth.css'

const FRAME_LABELS = {
  application: 'Application',
  domain: 'Domain',
  process: 'Process',
  infoflow: 'Information Flow',
  landscape: 'Landscape',
  site: 'Site',
  brand: 'Brand',
  activity: 'Activity',
  capability: 'Capability',
  matrix: 'Matrix',
  dependency: 'Dependency',
}

/** Mirrors DashboardCards' ISSUE_CARDS labels (copied, not shared — matches
 * the existing duplication between these two files). */
const ISSUE_LABELS = {
  HUB: 'Hubs',
  CIRCULAR_DEPENDENCY: 'Circular dependencies',
  BROKEN_RELATIONSHIP_REFERENCE: 'Broken relationships',
  DANGLING_INTERFACE_CONSUMER: 'Dangling interface consumers',
  BROKEN_INFORMATION_FLOW_REFERENCE: 'Broken information flows',
  UNMAPPED_PROCESS_APPLICATION: 'Unmapped process applications',
  DUPLICATE_APPLICATION: 'Duplicate applications',
  ORPHAN_APPLICATION: 'Orphan applications',
  OWNERSHIP_RECORD_MISSING: 'Ownership record missing',
  OWNERSHIP_PARTIAL_GAP: 'Ownership partial gaps',
  MISSING_OWNER_FIELD: 'Missing owner field',
  LIFECYCLE_RISK_CRITICAL_PROCESS: 'EoL on critical process',
  LIFECYCLE_RISK_EOL_PROVIDER: 'EoL interface providers',
  LIFECYCLE_INCONSISTENCY: 'Lifecycle inconsistencies',
  DEPRECATED_INTERFACE_IN_USE: 'Deprecated interfaces in use',
  INTERFACE_WITHOUT_RELATIONSHIP: 'Interfaces without a relationship',
  SENSITIVE_DATA_INSECURE_FLOW: 'Sensitive data at risk',
  PHASE_OUT_CRITICAL_PATH: 'Phase-out in critical path',
}

function scoreState(score) {
  if (score >= 85) return { label: 'Healthy', tone: 'healthy' }
  if (score >= 65) return { label: 'Needs attention', tone: 'watch' }
  return { label: 'At risk', tone: 'risk' }
}

function ArchitectureHealth({
  frame,
  elements = [],
  findings = [],
  loading = false,
  error = null,
  activeType = null,
  onSelect,
}) {
  const [expanded, setExpanded] = useState(true)

  const health = useMemo(() => {
    const entityIds = new Set(
      elements
        .filter((element) => !element.data?.source && !element.data?.target)
        .map((element) => element.data?.id)
        .filter(Boolean),
    )
    const scopedFindings = findings.filter((finding) =>
      (finding?.relatedEntityIds ?? []).some((id) => entityIds.has(id)),
    )
    const affectedIds = new Set(
      scopedFindings.flatMap((finding) => finding.relatedEntityIds.filter((id) => entityIds.has(id))),
    )
    const counts = scopedFindings.reduce((result, finding) => {
      if (finding?.type) result[finding.type] = (result[finding.type] ?? 0) + 1
      return result
    }, {})
    const risks = Object.entries(counts)
      .map(([type, count]) => ({ type, count, label: ISSUE_LABELS[type] ?? type }))
      .sort((first, second) => second.count - first.count)
    // Score = the percentage of this frame's entities with NO finding touching them,
    // i.e. 100 * (1 - affected/total), rounded and floored at 0. affectedIds is a
    // deduplicated union (an entity hit by two different finding types still only
    // counts once), so fixing one entity's only finding always raises the score by
    // ~100/entityCount points — monotonic by construction, not by branching logic.
    const score = entityIds.size === 0
      ? 100
      : Math.max(0, Math.round(100 * (1 - affectedIds.size / entityIds.size)))

    return { score, entityCount: entityIds.size, affectedCount: affectedIds.size, risks }
  }, [elements, findings])

  const state = scoreState(health.score)
  const frameLabel = FRAME_LABELS[frame] ?? frame

  return (
    <section className={`architecture-health architecture-health--${state.tone}`} aria-label={`${frameLabel} architecture health`}>
      <button
        type="button"
        className="architecture-health-toggle"
        onClick={() => setExpanded((current) => !current)}
        aria-expanded={expanded}
      >
        <span className="architecture-health-icon" aria-hidden="true"><Gauge size={17} /></span>
        <span className="architecture-health-title">
          <strong>Architecture Health</strong>
          <small>{frameLabel} frame</small>
        </span>
        <span
          className="architecture-health-score"
          style={{ '--health-score': `${health.score * 3.6}deg` }}
          aria-label={`${health.score} out of 100`}
        >
          {loading ? '...' : health.score}
        </span>
        <ChevronDown className={`architecture-health-chevron${expanded ? ' is-open' : ''}`} size={16} aria-hidden="true" />
      </button>

      {expanded && (
        <div className="architecture-health-body">
          {error ? (
            <p className="architecture-health-message">Health data is unavailable.</p>
          ) : loading ? (
            <p className="architecture-health-message">Assessing this frame...</p>
          ) : (
            <>
              <div className="architecture-health-summary">
                <strong>{state.label}</strong>
                <span>{health.affectedCount} of {health.entityCount} entities have findings</span>
              </div>
              {health.risks.length > 0 ? (
                <div className="architecture-health-risks" aria-label="Health findings">
                  {health.risks.map((risk) => (
                    <button
                      key={risk.type}
                      type="button"
                      className={`architecture-health-risk${activeType === risk.type ? ' is-active' : ''}`}
                      onClick={() => onSelect?.(activeType === risk.type ? null : risk.type)}
                      aria-pressed={activeType === risk.type}
                      title={`Highlight ${risk.label.toLowerCase()} in the graph`}
                    >
                      <AlertTriangle size={13} aria-hidden="true" />
                      <span>{risk.label}</span>
                      <strong>{risk.count}</strong>
                    </button>
                  ))}
                </div>
              ) : (
                <p className="architecture-health-message architecture-health-message--clear">No findings in this frame.</p>
              )}
            </>
          )}
        </div>
      )}
    </section>
  )
}

export default ArchitectureHealth