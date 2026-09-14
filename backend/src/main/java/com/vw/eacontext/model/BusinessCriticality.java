package com.vw.eacontext.model;

/**
 * Business importance of an {@link Application}.
 *
 * <p>Not used for a {@link BusinessProcess}'s own criticality — see
 * {@link ProcessCriticality}, a separate 3-value type with no
 * {@code ADMINISTRATIVE} tier.</p>
 */
public enum BusinessCriticality {
    MISSION_CRITICAL,
    BUSINESS_CRITICAL,
    BUSINESS_OPERATIONAL,
    ADMINISTRATIVE
}
