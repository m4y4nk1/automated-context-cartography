package com.vw.eacontext.exception;

/**
 * Unchecked exception thrown when an EA data source cannot be parsed or mapped
 * into the canonical model.
 */
public class EaIngestionException extends RuntimeException {

    public EaIngestionException(String message) {
        super(message);
    }

    public EaIngestionException(String message, Throwable cause) {
        super(message, cause);
    }
}

