package com.vw.eacontext.dto;

import java.util.Arrays;
import java.util.stream.Collectors;

/** The observation frames exposed by the API, with their URL slugs. */
public enum Frame {
    APPLICATION("application"),
    PROCESS("process"),
    DOMAIN("domain"),
    INFO_FLOW("infoflow");

    private final String slug;

    Frame(String slug) {
        this.slug = slug;
    }

    public String slug() {
        return slug;
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
