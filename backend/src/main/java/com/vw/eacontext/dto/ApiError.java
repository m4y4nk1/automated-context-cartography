package com.vw.eacontext.dto;

import java.time.Instant;
import java.util.List;

/**
 * A standard, structured error payload returned by the API.
 *
 * @param timestamp when the error occurred
 * @param status    the HTTP status code
 * @param error     the HTTP reason phrase
 * @param message   a human-readable description
 * @param details   optional field-level or contextual details (e.g. validation messages)
 */
public record ApiError(Instant timestamp, int status, String error, String message, List<String> details) {

    public ApiError {
        details = details == null ? List.of() : List.copyOf(details);
    }

    public static ApiError of(int status, String error, String message) {
        return new ApiError(Instant.now(), status, error, message, List.of());
    }

    public static ApiError of(int status, String error, String message, List<String> details) {
        return new ApiError(Instant.now(), status, error, message, details);
    }
}

