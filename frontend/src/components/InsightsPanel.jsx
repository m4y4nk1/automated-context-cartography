import ArchitectureHealth from './ArchitectureHealth'
import './InsightsPanel.css'

/**
 * Right-panel AI summary populated from the backend getSummary() call.
 *
 * @param {object} props
 * @param {string} [props.summary] - Natural-language summary text.
 * @param {boolean} [props.summaryLoading] - Whether the summary is loading.
 * @param {unknown} [props.summaryError] - Error from the summary fetch, if any.
 * @param {{declaredCount: number, detectedCount: number, newlyDetected: Array<object>} | null}
 *   [props.gapComparison] - Declared-vs-detected data-quality gap comparison, if available.
 * @param {boolean} [props.gapComparisonLoading] - Whether the gap comparison is loading.
 */
function InsightsPanel({
  summary,
  summaryLoading = false,
  summaryError = null,
  gapComparison = null,
  gapComparisonLoading = false,
  frame,
  elements,
  findings,
  findingsLoading = false,
  findingsError = null,
  activeType = null,
  onIssueSelect,
}) {
  return (
    <div className="insights-content">
      <ArchitectureHealth
        frame={frame}
        elements={elements}
        findings={findings}
        loading={findingsLoading}
        error={findingsError}
        activeType={activeType}
        onSelect={onIssueSelect}
      />
      <h3 className="insights-label">AI Summary</h3>
      {!gapComparisonLoading && gapComparison && (
        <p className="insights-gap-summary">
          {gapComparison.declaredCount} declared gap(s), {gapComparison.newlyDetected?.length ?? 0} additional issue(s) detected
        </p>
      )}
      <div className="ai-box">
        {summaryLoading ? (
          <span className="ai-box-loading">
            <span className="ai-box-spinner" aria-hidden="true" />
            Generating summary…
          </span>
        ) : summaryError ? (
          <span className="ai-box-error">Couldn’t load the summary.</span>
        ) : summary ? (
          summary
        ) : (
          <span className="ai-box-muted">No summary available.</span>
        )}
      </div>

    </div>
  )
}

export default InsightsPanel

