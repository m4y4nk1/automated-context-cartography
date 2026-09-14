package com.vw.eacontext.model;

import java.util.List;

import lombok.Builder;

/**
 * The canonical, in-memory representation of an entire EA dataset after
 * ingestion — the single source of truth consumed by the graph and insight
 * layers.
 *
 * <p>Each list is guaranteed to be non-null (empty rather than {@code null})
 * so callers can iterate without null checks.</p>
 *
 * @param applications          applications (systems) — the node table
 * @param relationships         directed application-to-application dependency edges
 * @param interfaces            integrations between applications
 * @param informationObjects    information/data flows
 * @param businessProcesses     business processes
 * @param processMappings       business-process-to-application mappings
 * @param applicationOwnerships application ownership/accountability records
 * @param dataQualityGaps       pre-declared, partial known data-quality gaps
 * @param ingestionNotes        notes about ingestion issues
 */
@Builder(toBuilder = true)
public record CanonicalModel(
        List<Application> applications,
        List<Relationship> relationships,
        List<Interface> interfaces,
        List<InformationObject> informationObjects,
        List<BusinessProcess> businessProcesses,
        List<ProcessMapping> processMappings,
        List<ApplicationOwnership> applicationOwnerships,
        List<DataQualityGap> dataQualityGaps,
        List<String> ingestionNotes
) {
    public CanonicalModel {
        applications = applications == null ? List.of() : List.copyOf(applications);
        relationships = relationships == null ? List.of() : List.copyOf(relationships);
        interfaces = interfaces == null ? List.of() : List.copyOf(interfaces);
        informationObjects = informationObjects == null ? List.of() : List.copyOf(informationObjects);
        businessProcesses = businessProcesses == null ? List.of() : List.copyOf(businessProcesses);
        processMappings = processMappings == null ? List.of() : List.copyOf(processMappings);
        applicationOwnerships = applicationOwnerships == null ? List.of() : List.copyOf(applicationOwnerships);
        dataQualityGaps = dataQualityGaps == null ? List.of() : List.copyOf(dataQualityGaps);
        ingestionNotes = ingestionNotes == null ? List.of() : List.copyOf(ingestionNotes);
    }
}
