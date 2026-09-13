package com.vw.eacontext.model;

import java.util.Locale;
import java.util.Map;

/**
 * Lifecycle state of an {@link Application} within the enterprise architecture.
 */
public enum LifecycleStatus {
    /** Under planning, not yet introduced. */
    PLAN,
    /** Being introduced into the landscape. */
    PHASE_IN,
    /** Actively used and supported. */
    ACTIVE,
    /** Being retired from the landscape. */
    PHASE_OUT,
    /** Retired; no longer supported. */
    END_OF_LIFE;

    /**
     * Normalized label -> constant. Keys are lower-cased and stripped of all
     * non-alphanumeric characters so "End of Life", "end-of-life" and
     * "END_OF_LIFE" all resolve identically.
     */
    private static final Map<String, LifecycleStatus> BY_LABEL = Map.of(
            "plan", PLAN,
            "phasein", PHASE_IN,
            "active", ACTIVE,
            "phaseout", PHASE_OUT,
            "endoflife", END_OF_LIFE);

    /**
     * Resolves a raw source label to a canonical constant.
     *
     * @param raw the raw source string (may be {@code null}/blank)
     * @return the matching constant, or {@code null} when the input is blank
     *         or the label is present but unrecognized
     */
    public static LifecycleStatus fromLabel(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String key = raw.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return BY_LABEL.get(key);
    }

    /** @return {@code true} for the retired status. */
    public boolean isEndOfLife() {
        return this == END_OF_LIFE;
    }

    /** @return {@code true} for statuses that carry lifecycle risk. */
    public boolean isRisky() {
        return this == END_OF_LIFE || this == PHASE_OUT;
    }

    /**
     * @return a human-readable label suitable for UI dropdowns, e.g.
     *         {@code PHASE_IN -> "Phase In"}.
     */
    public String label() {
        String[] words = name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder label = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (label.length() > 0) {
                label.append(' ');
            }
            label.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return label.toString();
    }
}
