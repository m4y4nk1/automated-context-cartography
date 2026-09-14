package com.vw.eacontext.dto;

import java.util.Arrays;
import java.util.stream.Collectors;

/** The observation frames exposed by the API, with their URL slugs. */
public enum Frame {
    APPLICATION("application", "Application"),
    PROCESS("process", "Process"),
    DOMAIN("domain", "Domain"),
    INFO_FLOW("infoflow", "Information Flow");

    private final String slug;
    private final String label;

    Frame(String slug, String label) {
        this.slug = slug;
        this.label = label;
    }

    public String slug() {
        return slug;
    }

    /** Human-readable frame name, e.g. for exported diagram/page titles. */
    public String label() {
        return label;
    }

    /** Comma-separated list of every valid slug, for error messages. */
    public static String slugs() {
        return Arrays.stream(values()).map(Frame::slug).collect(Collectors.joining(", "));
    }

    /**
     * Resolves a frame from its URL slug (case-insensitive).
     *
     * @param slug the slug (e.g. {@code application})
     * @return the matching frame
     * @throws IllegalArgumentException if the slug is unknown
     */
    public static Frame fromSlug(String slug) {
        return Arrays.stream(values())
                .filter(f -> f.slug.equalsIgnoreCase(slug))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown frame '" + slug + "'. Valid values: " + slugs()));
    }
}
