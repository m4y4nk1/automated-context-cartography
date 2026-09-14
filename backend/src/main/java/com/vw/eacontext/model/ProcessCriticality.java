package com.vw.eacontext.model;

/**
 * Criticality of a {@link BusinessProcess} from one {@link ProcessMapping}'s
 * perspective.
 *
 * <p>Deliberately a separate type from {@link BusinessCriticality} even though
 * two of its three values read identically: the dataset's ProcessCriticality
 * column is a strict 3-value enum with no {@code Administrative} tier — a
 * business process is never "administrative" the way a single application can
 * be. Reusing {@link BusinessCriticality} here would silently accept that
 * fourth value on a modified dataset where the spec forbids it.</p>
 */
public enum ProcessCriticality {
    MISSION_CRITICAL,
    BUSINESS_CRITICAL,
    BUSINESS_OPERATIONAL
}
