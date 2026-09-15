import { useState } from 'react'
import { getSimulatedRemoval } from '../services/api'
import { findingsForEntity } from '../services/insightAdapter'
import './NodeDetail.css'

/**
 * Human-readable labels for the issue classes carried on a node.
 * @see services/insightAdapter.js
 */
const BADGES = {
  gap: { label: 'Ownership gap', className: 'badge--gap' },
  eol: { label: 'Lifecycle risk', className: 'badge--eol' },
  spof: { label: 'Single point of failure', className: 'badge--spof' },
  circular: { label: 'Circular dependency', className: 'badge--circular' },
  'orphan-interface': { label: 'Dangling interface', className: 'badge--orphan-interface' },
  unmapped: { label: 'Unmapped process application', className: 'badge--unmapped' },
  'broken-ref': { label: 'Broken reference', className: 'badge--broken-ref' },
  duplicate: { label: 'Duplicate application record', className: 'badge--duplicate' },
  orphan: { label: 'Orphan application', className: 'badge--orphan' },
  'lifecycle-risk': { label: 'Lifecycle risk', className: 'badge--lifecycle-risk' },
  'deprecated-interface': { label: 'Deprecated interface in use', className: 'badge--deprecated-interface' },
  'sensitive-flow': { label: 'Sensitive data risk', className: 'badge--sensitive-flow' },
}

/** Acronyms kept upper-cased rather than title-cased by {@link humanizeEnum}. */
const ACRONYMS = new Set(['PII', 'PCI', 'SAAS', 'COTS'])

/** {@code MISSION_CRITICAL -> "Mission Critical"}, {@code CONFIDENTIAL_PII -> "Confidential PII"}. */
function humanizeEnum(value) {
  if (!value) return value
  return value
    .split('_')
    .map((word) => (ACRONYMS.has(word) ? word : word.charAt(0) + word.slice(1).toLowerCase()))
    .join(' ')
}

/**
 * A connection's kind. Application-frame edges carry a backend-provided
 * `edgeTypes` category (`DEPENDENCY` | `INTERFACE` | `FLOW`) — used instead of
 * `type`, which for a dependency edge is the specific `RelationshipType` name
 * and varies per row. Other frames' edges (an application node also appears in
 * the process and information-flow frames) are identified by `type` alone.
 */
function connectionKind(conn) {
  return conn.edgeTypes?.[0] ?? conn.type
}

function connectionKindLabel(conn) {
  switch (connectionKind(conn)) {
    case 'INTERFACE': return 'Interface'
    case 'FLOW': return 'Information flow'
    case 'DEPENDENCY': return humanizeEnum(conn.type) || 'Dependency'
    case 'processMapping': return 'Supports process'
    case 'produces': return 'Produces'
    case 'consumes': return 'Consumes'
    default: return conn.type ?? 'Connection'
  }
}

/** One line of the connection's most relevant business metadata, by kind. */
function connectionSummary(conn) {
  switch (connectionKind(conn)) {
    case 'DEPENDENCY':
      return conn.dependencyCriticality ? `${humanizeEnum(conn.dependencyCriticality)} criticality` : null
    case 'INTERFACE':
      return conn.protocols?.[0] ?? null
    case 'FLOW':
      return conn.classifications?.[0] ? humanizeEnum(conn.classifications[0]) : null
    case 'processMapping':
      return [humanizeEnum(conn.roleOfApplication), humanizeEnum(conn.processCriticality)]
        .filter(Boolean).join(' · ') || null
    case 'produces':
    case 'consumes':
      return [conn.flowId, humanizeEnum(conn.operation)].filter(Boolean).join(' · ') || null
    default:
      return null
  }
}

/**
 * A single label/value row. Renders nothing when the value is blank, unless a
 * `fallback` is supplied (used in the Ownership section, where a blank field
 * is itself informative rather than something to hide).
 */
function DetailRow({ label, value, fallback }) {
  const isBlank = value === null || value === undefined || value === ''
  if (isBlank && fallback === undefined) return null
  return (
    <div className="detail-row">
      <span className="detail-key">{label}</span>
      <span className="detail-val">{isBlank ? fallback : value}</span>
    </div>
  )
}

/**
 * Shows the selected node's details and issue badges, populated from the node
 * data emitted by GraphCanvas' onNodeSelect. Renders a placeholder when nothing
 * is selected.
 *
 * @param {object} props
 * @param {object | null} [props.node] - Selected node data (id, label, type,
 *   businessDomain, businessCriticality, lifecycleStatus, lifecycleStartDate,
 *   lifecycleEndDate, hosting, vendorType, costCenter, description,
 *   hasOwnershipRecord, applicationOwner, ownerEmployeeId (the Application
 *   record's own claim), ownershipEmployeeId (the ApplicationOwnership
 *   record's own claim), systemCustodian, businessOwner, supportGroup,
 *   department, declaredGaps ({gapType, description, severity}[], from
 *   KnownDataQualityGaps), classification, sensitive, connections,
 *   connectionsDetail ({id, label, type, edgeTypes, direction, otherId,
 *   otherLabel, ...}[], application nodes only), ...).
 * @param {string} [props.issueClasses] - Space-separated issue classes for the
 *   selected node (e.g. "gap eol"), used to render badges.
 * @param {Array<object>} [props.findings] - The full findings list (from
 *   useInsights), used to show each badge's underlying source-record detail
 *   (finding.message) — traceability from an on-graph indicator back to the
 *   specific record that produced it, not just a category label.
 * @param {boolean} [props.hideTitle] - Skips the built-in title row, used when
 *   an outer container (e.g. NodePopupDialog) already renders the node name.
 */
function NodeDetail({ node, issueClasses = '', findings = [], hideTitle = false }) {
  // Hooks must run unconditionally (before the `!node` early return below).
  // Callers key this component on `node.id` (see NodePopupDialog.jsx) so this
  // local state naturally resets via remount when a different node is picked
  // — no effect needed to clear it by hand.
  const [simulation, setSimulation] = useState(null)
  const [simulationLoading, setSimulationLoading] = useState(false)
  const [simulationError, setSimulationError] = useState(null)

  if (!node) {
    return (
      <div className="node-detail node-detail--empty">
        Click a node to inspect its details and blast radius.
      </div>
    )
  }

  const badges = issueClasses
    .split(' ')
    .filter((cls) => BADGES[cls])
  const nodeFindings = findingsForEntity(findings, node.id)

  const runSimulation = async () => {
    setSimulationLoading(true)
    setSimulationError(null)
    try {
      setSimulation(await getSimulatedRemoval(node.id))
    } catch {
      setSimulationError("Couldn't run the simulation. Please try again.")
    } finally {
      setSimulationLoading(false)
    }
  }

  return (
    <div className="node-detail">
      {!hideTitle && <div className="node-detail-title">{node.label ?? node.id}</div>}

      <DetailRow label="Domain" value={node.businessDomain} />
      <DetailRow label="Criticality" value={humanizeEnum(node.businessCriticality)} />
      <DetailRow label="Lifecycle" value={humanizeEnum(node.lifecycleStatus)} />
      <DetailRow label="Since" value={node.lifecycleStartDate} />
      <DetailRow label="Until" value={node.lifecycleEndDate} />
      <DetailRow label="Hosting" value={humanizeEnum(node.hosting)} />
      <DetailRow label="Vendor Type" value={humanizeEnum(node.vendorType)} />
      <DetailRow label="Cost Center" value={node.costCenter} />
      <DetailRow label="Description" value={node.description} />

      {node.type === 'application' ? (
        <div className="node-detail-section">
          <div className="node-detail-subtitle">
            Connections{typeof node.connections === 'number' ? ` (${node.connections})` : ''}
          </div>
          {!node.connectionsDetail || node.connectionsDetail.length === 0 ? (
            <div className="node-detail-empty-note">No connections</div>
          ) : (
            <ul className="node-detail-connections">
              {node.connectionsDetail.map((conn) => (
                <li key={conn.id ?? `${conn.otherId}-${conn.direction}`} className="node-detail-connection">
                  <span className="node-detail-connection-main">
                    {conn.direction === 'outgoing' ? '→' : '←'} {conn.otherLabel ?? conn.otherId}
                  </span>
                  <span className="node-detail-connection-meta">
                    {connectionKindLabel(conn)}
                    {connectionSummary(conn) ? ` · ${connectionSummary(conn)}` : ''}
                  </span>
                </li>
              ))}
            </ul>
          )}
        </div>
      ) : (
        <DetailRow
          label="Connections"
          value={
            typeof node.connections === 'number'
              ? `${node.connections} (blast radius)`
              : undefined
          }
        />
      )}

      {node.type === 'application' && (
        <div className="node-detail-section">
          <div className="node-detail-subtitle">Ownership</div>
          {node.hasOwnershipRecord === false ? (
            <div className="node-detail-empty-note">No ownership record</div>
          ) : (
            <>
              <DetailRow label="Owner" value={node.applicationOwner} fallback="Not assigned" />
              <DetailRow label="Employee ID" value={node.ownershipEmployeeId} fallback="Not assigned" />
              <DetailRow label="System Custodian" value={node.systemCustodian} fallback="Not assigned" />
              <DetailRow label="Business Owner" value={node.businessOwner} fallback="Not assigned" />
              <DetailRow label="Support Group" value={node.supportGroup} fallback="Not assigned" />
              <DetailRow label="Department" value={node.department} fallback="Not assigned" />
            </>
          )}
        </div>
      )}

      {node.type === 'application' && node.declaredGaps?.length > 0 && (
        <div className="node-detail-section">
          <div className="node-detail-subtitle">Known Data-Quality Gaps</div>
          {node.declaredGaps.map((gap, index) => (
            <p key={index} className="node-detail-trace">
              <strong>{gap.gapType ?? 'Gap'}</strong>
              {gap.severity ? ` (${humanizeEnum(gap.severity)})` : ''}
              {gap.description ? ` — ${gap.description}` : ''}
            </p>
          ))}
        </div>
      )}

      {node.type === 'informationObject' && (
        <DetailRow label="Classification" value={humanizeEnum(node.classification)} />
      )}

      {(badges.length > 0 || node.sensitive) && (
        <div className="node-detail-badges">
          {badges.map((cls) => (
            <span key={cls} className={`badge ${BADGES[cls].className}`}>
              {BADGES[cls].label}
            </span>
          ))}
          {node.sensitive && <span className="badge badge--sensitive">Sensitive data</span>}
        </div>
      )}

      {nodeFindings.length > 0 && (
        <div className="node-detail-section">
          <div className="node-detail-subtitle">Source records</div>
          {nodeFindings.map((finding, index) => (
            <p key={`${finding.type}-${index}`} className="node-detail-trace">
              {finding.message}
            </p>
          ))}
        </div>
      )}

      {node.type === 'application' && (
        <div className="node-detail-section">
          <div className="node-detail-subtitle">What if this app were retired?</div>

          {!simulation && (
            <button
              type="button"
              className="node-detail-simulate-btn"
              onClick={runSimulation}
              disabled={simulationLoading}
            >
              {simulationLoading ? 'Simulating…' : 'Simulate retiring this application'}
            </button>
          )}

          {simulationError && <div className="node-detail-empty-note">{simulationError}</div>}

          {simulation && (
            <>
              <p className="node-detail-trace">
                {simulation.downstream.length} application(s) depend on this one, directly or
                indirectly (downstream); it relies on {simulation.upstream.length} (upstream).
              </p>

              {simulation.newFindings.length > 0 && (
                <>
                  <div className="node-detail-simulate-label node-detail-simulate-label--new">
                    New problems this would create ({simulation.newFindings.length})
                  </div>
                  {simulation.newFindings.map((finding, index) => (
                    <p key={`new-${index}`} className="node-detail-trace">{finding.message}</p>
                  ))}
                </>
              )}

              {simulation.resolvedFindings.length > 0 && (
                <>
                  <div className="node-detail-simulate-label node-detail-simulate-label--resolved">
                    Problems this would resolve ({simulation.resolvedFindings.length})
                  </div>
                  {simulation.resolvedFindings.map((finding, index) => (
                    <p key={`resolved-${index}`} className="node-detail-trace">{finding.message}</p>
                  ))}
                </>
              )}

              {simulation.newFindings.length === 0 && simulation.resolvedFindings.length === 0 && (
                <div className="node-detail-empty-note">No architectural findings would change.</div>
              )}

              <button
                type="button"
                className="node-detail-simulate-btn node-detail-simulate-btn--secondary"
                onClick={runSimulation}
                disabled={simulationLoading}
              >
                {simulationLoading ? 'Simulating…' : 'Re-run simulation'}
              </button>
            </>
          )}
        </div>
      )}
    </div>
  )
}

export default NodeDetail
