package com.vw.eacontext.insight;

/**
 * The category of an insight {@link Finding} — one value per detector in the
 * Auriga Motors anomaly catalogue (dataset report, section 4).
 */
public enum FindingType {
    /** An application with unusually high in-degree in the relationship graph. */
    HUB,
    /** A directed cycle among applications in the Relationships graph. */
    CIRCULAR_DEPENDENCY,
    /** A Relationship whose target application does not exist. */
    BROKEN_RELATIONSHIP_REFERENCE,
    /** An Interface whose consumer application does not exist. */
    DANGLING_INTERFACE_CONSUMER,
    /** An InformationObject whose source or target application does not exist. */
    BROKEN_INFORMATION_FLOW_REFERENCE,
    /** A ProcessMapping whose supporting application does not exist. */
    UNMAPPED_PROCESS_APPLICATION,
    /** Two or more applications sharing the same name. */
    DUPLICATE_APPLICATION,
    /** An application referenced by no relationship, interface, flow, or process mapping. */
    ORPHAN_APPLICATION,
    /** An application with no ApplicationOwnership record at all. */
    OWNERSHIP_RECORD_MISSING,
    /** An ApplicationOwnership record with a blank business owner. */
    OWNERSHIP_PARTIAL_GAP,
    /** An application record with a blank OwnerEmployeeID. */
    MISSING_OWNER_FIELD,
    /** An end-of-life application supporting a mission-critical process. */
    LIFECYCLE_RISK_CRITICAL_PROCESS,
    /** An active interface whose provider application is end of life. */
    LIFECYCLE_RISK_EOL_PROVIDER,
    /** An Active application with a lifecycle end date already in the past. */
    LIFECYCLE_INCONSISTENCY,
    /** An information flow travelling over a deprecated interface. */
    DEPRECATED_INTERFACE_IN_USE,
    /** An interface provider/consumer pair with no corresponding relationship. */
    INTERFACE_WITHOUT_RELATIONSHIP,
    /** Sensitive (PII/PCI) data flowing over an insecure or deprecated interface. */
    SENSITIVE_DATA_INSECURE_FLOW,
    /** A phase-out application still supporting a mission-critical process. */
    PHASE_OUT_CRITICAL_PATH
}
