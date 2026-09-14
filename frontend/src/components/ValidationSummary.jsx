import { AlertTriangle, ArrowLeft, CheckCircle2, CircleAlert, Info, XCircle } from 'lucide-react'
import './ValidationSummary.css'

const SEVERITY_ICON = {
  ERROR: XCircle,
  WARNING: AlertTriangle,
  INFO: Info,
}

/**
 * An issue with no specific record/field attached is a whole-file/whole-sheet
 * problem (nothing recognizable was found, a required column is missing
 * entirely, a table didn't match any known entity, ...) rather than a
 * data-quality gap on an otherwise-parsed record. Both `recordId` and `field`
 * come back null from the backend in exactly this case — see
 * ValidationIssue's javadoc — so no message-text sniffing is needed to tell
 * the two apart.
 */
function isStructural(issue) {
  return issue.recordId == null && issue.field == null
}

function IssueRow({ issue }) {
  const Icon = SEVERITY_ICON[issue.severity] ?? Info
  return (
    <li className={`validation-issue-row validation-issue-row--${issue.severity?.toLowerCase()}`}>
      <Icon size={15} className="validation-issue-icon" aria-hidden="true" />
      <div className="validation-issue-body">
        <span className="validation-issue-message">{issue.message}</span>
        {(issue.sheet || issue.recordId || issue.field) && (
          <span className="validation-issue-meta">
            {[issue.sheet, issue.recordId, issue.field].filter(Boolean).join(' · ')}
          </span>
        )}
      </div>
    </li>
  )
}

/**
 * Shown after a file has been uploaded and successfully parsed, before the
 * user lands in the main workspace. Separates issues that mean the file
 * itself needs to be fixed and re-uploaded (structural — nothing recognizable
 * found, a whole required column missing, an unrecognized table skipped)
 * from ordinary data-quality findings on records that parsed fine (ghost
 * references, missing required fields, duplicate ids, ...) — the latter are
 * already captured and browsable as findings once inside the workspace, so
 * they don't block continuing.
 *
 * @param {object} props
 * @param {object} props.report - The ValidationReport returned by /api/upload.
 * @param {() => void} props.onContinue - Proceed into the workspace with this report.
 * @param {() => void} props.onUploadDifferent - Discard this report and return to the uploader.
 */
function ValidationSummary({ report, onContinue, onUploadDifferent }) {
  const issues = report?.issues ?? []
  const summary = report?.summary ?? { errorCount: 0, warningCount: 0, infoCount: 0, bySheet: {} }

  const structuralIssues = issues.filter(isStructural)
  const dataQualityIssues = issues.filter((issue) => !isStructural(issue))
  const blocking = structuralIssues.some((issue) => issue.severity === 'ERROR')
  const clean = issues.length === 0

  return (
    <section className="validation-summary" aria-labelledby="validation-summary-title">
      <div className={`validation-summary-banner validation-summary-banner--${clean ? 'clean' : blocking ? 'blocking' : 'ok'}`}>
        {clean ? <CheckCircle2 size={22} aria-hidden="true" /> : blocking ? <XCircle size={22} aria-hidden="true" /> : <CircleAlert size={22} aria-hidden="true" />}
        <div>
          <h1 id="validation-summary-title">
            {clean
              ? 'No validation issues found'
              : blocking
                ? 'This file has structural problems'
                : 'Dataset loaded with some findings'}
          </h1>
          <p>
            {clean
              ? 'Every check passed — you can continue into the workspace.'
              : blocking
                ? "Nothing usable could be found for at least one required part of this file. You can still continue, but the graph will be missing data — fixing and re-uploading is recommended."
                : 'The file parsed successfully. The findings below were captured and remain browsable inside the workspace.'}
          </p>
        </div>
      </div>

      <div className="validation-summary-counts">
        <span className="validation-count validation-count--error">{summary.errorCount} error{summary.errorCount === 1 ? '' : 's'}</span>
        <span className="validation-count validation-count--warning">{summary.warningCount} warning{summary.warningCount === 1 ? '' : 's'}</span>
        <span className="validation-count validation-count--info">{summary.infoCount} info</span>
      </div>

      {structuralIssues.length > 0 && (
        <div className="validation-summary-section">
          <h2>
            File-level notes{' '}
            <span className="validation-section-hint">
              {blocking ? '— needs a new file' : '— didn\'t block parsing, informational only'}
            </span>
          </h2>
          <ul className="validation-issue-list">
            {structuralIssues.map((issue, index) => (
              <IssueRow key={index} issue={issue} />
            ))}
          </ul>
        </div>
      )}

      {dataQualityIssues.length > 0 && (
        <div className="validation-summary-section">
          <h2>Data-quality findings <span className="validation-section-hint">— captured, visible in the workspace</span></h2>
          <ul className="validation-issue-list validation-issue-list--scroll">
            {dataQualityIssues.map((issue, index) => (
              <IssueRow key={index} issue={issue} />
            ))}
          </ul>
        </div>
      )}

      <div className="validation-summary-actions">
        <button type="button" className="validation-btn validation-btn--ghost" onClick={onUploadDifferent}>
          <ArrowLeft size={15} aria-hidden="true" />
          Upload a different file
        </button>
        <button
          type="button"
          className={`validation-btn ${blocking ? 'validation-btn--ghost' : 'validation-btn--primary'}`}
          onClick={onContinue}
        >
          {blocking ? 'Continue anyway' : 'Continue to workspace'}
        </button>
      </div>
    </section>
  )
}

export default ValidationSummary
