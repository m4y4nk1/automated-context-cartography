package com.vw.eacontext.dto;

import lombok.Builder;

/**
 * High-level statistics about a parsed EA landscape / its graph, used for
 * summaries and dashboards.
 *
 * @param applicationCount           number of applications
 * @param relationshipCount          number of relationship rows in the dataset (including any
 *                                    that reference a ghost application — those are surfaced
 *                                    separately as broken-reference findings, not silently
 *                                    dropped from this count)
 * @param interfaceCount             number of interfaces
 * @param informationObjectCount     number of information flows
 * @param businessProcessCount       number of business processes
 * @param domainCount                number of distinct business domains
 * @param mostConnectedApplicationId id of the highest in-degree application (may be {@code null})
 * @param maxDegree                  in-degree of the most connected application
 * @param cycleCount                 number of circular-dependency chains detected
 * @param hubCount                   number of applications flagged as hubs
 * @param orphanCount                number of applications referenced nowhere
 */
@Builder
public record GraphStats(
        int applicationCount,
        int relationshipCount,
        int interfaceCount,
        int informationObjectCount,
        int businessProcessCount,
        int domainCount,
        String mostConnectedApplicationId,
        int maxDegree,
        int cycleCount,
        int hubCount,
        int orphanCount) {
}
