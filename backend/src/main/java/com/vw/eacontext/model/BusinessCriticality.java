package com.vw.eacontext.model;

/**
 * Business importance of an {@link Application} or the {@link BusinessProcess}
 * it supports (via {@link ProcessMapping}).
 */
public enum BusinessCriticality {
    MISSION_CRITICAL,
    BUSINESS_CRITICAL,
    BUSINESS_OPERATIONAL,
    ADMINISTRATIVE
}
