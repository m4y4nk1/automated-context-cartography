package com.vw.eacontext.exception;

/**
 * Unchecked exception thrown when a requested entity (e.g. an application) does
 * not exist in the current model/graph.
 */
public class EaNotFoundException extends RuntimeException {

    public EaNotFoundException(String message) {
        super(message);
    }
}

