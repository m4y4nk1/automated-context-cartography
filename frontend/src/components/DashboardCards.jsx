import { useState } from 'react'
import {
  Cable,
  ChevronDown,
  ClockAlert,
  CircleOff,
  Copy,
  GitBranch,
  Link2Off,
  MapPinOff,
  Network,
  RefreshCcwDot,
  ShieldAlert,
  ShieldOff,
  Tag,
  Unlink,
  Unplug,
  UserRoundX,
  Users,
} from 'lucide-react'
import './DashboardCards.css'
import HorizontalScroller from './HorizontalScroller'

/**
 * The current 18-type anomaly catalogue. `severity` mirrors the fixed
 * severity each backend detector always produces for its finding type
 * (ERROR/WARNING/INFO never mix within a type), used to group cards below.
 */
const ISSUE_CARDS = [
  // --- ERROR (high severity) ---
  {
    type: 'HUB',
    label: 'Hubs',
    detail: 'Applications with unusually high in-degree (single points of failure)',
    icon: Network,
    tone: 'gold',
    severity: 'ERROR',
  },
  {
    type: 'CIRCULAR_DEPENDENCY',
    label: 'Circular dependencies',
    detail: 'Cycles in the relationship graph',
    icon: GitBranch,
    tone: 'gold',
    severity: 'ERROR',
  },
  {
    type: 'BROKEN_RELATIONSHIP_REFERENCE',
    label: 'Broken relationships',
    detail: 'Relationships targeting an application that does not exist',
    icon: Unlink,
    tone: 'coral',
    severity: 'ERROR',
  },
  {
    type: 'DANGLING_INTERFACE_CONSUMER',
    label: 'Dangling interface consumers',
    detail: 'Interfaces whose consumer application does not exist',
    icon: Unplug,
    tone: 'coral',
    severity: 'ERROR',
  },
  {
    type: 'BROKEN_INFORMATION_FLOW_REFERENCE',
    label: 'Broken information flows',
    detail: 'Information flows whose source or target application does not exist',
    icon: Link2Off,
    tone: 'coral',
    severity: 'ERROR',
  },
  {
    type: 'UNMAPPED_PROCESS_APPLICATION',
    label: 'Unmapped process applications',
    detail: 'Process mappings referencing an application that does not exist',
    icon: CircleOff,
    tone: 'slate',
    severity: 'ERROR',
  },
  {
    type: 'LIFECYCLE_RISK_CRITICAL_PROCESS',
    label: 'EoL on critical process',
    detail: 'End-of-life applications supporting a mission-critical process',
    icon: ClockAlert,
    tone: 'coral',
    severity: 'ERROR',
  },
  {
    type: 'DEPRECATED_INTERFACE_IN_USE',
    label: 'Deprecated interfaces in use',
    detail: 'Information flows relying on a deprecated interface',
    icon: ShieldAlert,
    tone: 'coral',
    severity: 'ERROR',
  },
  {
    type: 'SENSITIVE_DATA_INSECURE_FLOW',
    label: 'Sensitive data at risk',
    detail: 'Confidential/PII or restricted/PCI data over an insecure or deprecated interface',
    icon: ShieldOff,
    tone: 'coral',
    severity: 'ERROR',
  },
  // --- WARNING (medium severity) ---
  {
    type: 'DUPLICATE_APPLICATION',
    label: 'Duplicate applications',
    detail: 'Two or more applications sharing the same name',
    icon: Copy,
    tone: 'amber',
    severity: 'WARNING',
  },
  {
    type: 'ORPHAN_APPLICATION',
    label: 'Orphan applications',
    detail: 'Applications referenced by no relationship, interface, flow, or process',
    icon: MapPinOff,
    tone: 'slate',
    severity: 'WARNING',
  },
  {
    type: 'OWNERSHIP_RECORD_MISSING',
    label: 'Ownership record missing',
    detail: 'Applications with no ownership record at all',
    icon: UserRoundX,
    tone: 'coral',
    severity: 'WARNING',
  },
  {
    type: 'MISSING_OWNER_FIELD',
    label: 'Missing owner field',
    detail: 'Applications with a blank owner employee ID',
    icon: UserRoundX,
    tone: 'coral',
    severity: 'WARNING',
  },
  {
    type: 'LIFECYCLE_RISK_EOL_PROVIDER',
    label: 'EoL interface providers',
    detail: 'Active interfaces provided by an end-of-life application',
    icon: ClockAlert,
    tone: 'amber',
    severity: 'WARNING',
  },
  {
    type: 'LIFECYCLE_INCONSISTENCY',
    label: 'Lifecycle inconsistencies',
    detail: 'Active applications with a lifecycle end date already in the past',
    icon: RefreshCcwDot,
    tone: 'amber',
    severity: 'WARNING',
  },
  {
    type: 'INTERFACE_WITHOUT_RELATIONSHIP',
    label: 'Interfaces without a relationship',
    detail: 'Interface provider/consumer pairs with no corresponding relationship',
    icon: Cable,
    tone: 'amber',
    severity: 'WARNING',
  },
  {
    type: 'PHASE_OUT_CRITICAL_PATH',
    label: 'Phase-out in critical path',
    detail: 'Phase-out applications still supporting a mission-critical process',
    icon: Tag,
    tone: 'amber',
    severity: 'WARNING',
  },
  // --- INFO (low severity) ---
  {
    type: 'OWNERSHIP_PARTIAL_GAP',
    label: 'Ownership partial gaps',
    detail: 'Ownership records with no business owner assigned',
    icon: Users,
    tone: 'slate',
    severity: 'INFO',
  },
]

const SEVERITY_GROUPS = [
  { severity: 'ERROR', label: 'High severity', expandedByDefault: true },
  { severity: 'WARNING', label: 'Medium severity', expandedByDefault: false },
  { severity: 'INFO', label: 'Low severity', expandedByDefault: false },
]

function IssueItem({ icon: Icon, label, value, detail, tone, active, onClick }) {
  return (
    <button
      type="button"
      className={`issue-item issue-item--${tone}${active ? ' is-active' : ''}`}
      onClick={onClick}
      aria-pressed={active}
      title={`${label}: ${detail}`}
    >
      <span className="issue-item-icon" aria-hidden="true">
        <Icon size={16} strokeWidth={2.2} />
      </span>
      <span className="issue-item-label">{label}</span>
      <strong className="issue-item-count">{value}</strong>
      <span className="issue-item-meter" aria-hidden="true" />
    </button>
  )
}

/** One collapsible severity group (High/Medium/Low), following the same
 * toggle pattern used by ArchitectureHealth. */
function SeverityGroup({ group, cards, findings, loading, error, activeType, onSelect }) {
  const [expanded, setExpanded] = useState(group.expandedByDefault)
  const counts = findings.reduce((result, finding) => {
    if (finding?.type) result[finding.type] = (result[finding.type] ?? 0) + 1
    return result
  }, {})
  const visibleCards = cards.filter((card) => loading || error || (counts[card.type] ?? 0) > 0)

  if (visibleCards.length === 0 && !loading && !error) return null

  return (
    <div className="issue-severity-group">
      <button
        type="button"
        className="issue-severity-toggle"
        onClick={() => setExpanded((c) => !c)}
        aria-expanded={expanded}
      >
        <span className="issue-severity-label">{group.label}</span>
        <ChevronDown
          className={`issue-severity-chevron${expanded ? ' is-open' : ''}`}
          size={16}
          aria-hidden="true"
        />
      </button>
      {expanded && (
        <HorizontalScroller
          className="issue-navigator-scroller"
          contentClassName="issue-navigator-list"
          ariaLabel={`Detected issues (${group.label})`}
        >
          {visibleCards.map((card) => (
            <IssueItem
              key={card.type}
              icon={card.icon}
              label={card.label}
              value={loading || error ? '—' : counts[card.type] ?? 0}
              detail={error ? 'Insights unavailable' : loading ? 'Analyzing graph data' : card.detail}
              tone={card.tone}
              active={activeType === card.type}
              onClick={() => onSelect(card.type)}
            />
          ))}
        </HorizontalScroller>
      )}
    </div>
  )
}

function DashboardCards({
  findings,
  loading,
  error,
  activeType,
  onSelect,
}) {
  return (
    <section className="issue-navigator" aria-label="Detected issues">
      {SEVERITY_GROUPS.map((group) => (
        <SeverityGroup
          key={group.severity}
          group={group}
          cards={ISSUE_CARDS.filter((card) => card.severity === group.severity)}
          findings={findings}
          loading={loading}
          error={error}
          activeType={activeType}
          onSelect={onSelect}
        />
      ))}
    </section>
  )
}

export default DashboardCards
