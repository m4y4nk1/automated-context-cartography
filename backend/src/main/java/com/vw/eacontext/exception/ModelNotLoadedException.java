package com.vw.eacontext.exception;

/**
 * Thrown when an operation requires a dataset but none has been uploaded yet.
 * Maps to HTTP 409 (Conflict).
 */
public class ModelNotLoadedException extends RuntimeException {

    public ModelNotLoadedException(String message) {
        super(message);
    }
}

