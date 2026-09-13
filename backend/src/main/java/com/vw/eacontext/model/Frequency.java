package com.vw.eacontext.model;

/** How often an {@link Interface} exchanges data. */
public enum Frequency {
    REAL_TIME,
    NEAR_REAL_TIME,
    HOURLY_BATCH,
    DAILY_BATCH,
    WEEKLY_BATCH
}
