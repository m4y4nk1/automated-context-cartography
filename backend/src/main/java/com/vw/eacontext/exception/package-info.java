/**
 * Exception handling: custom exceptions and centralized error mapping.
 *
 * <p>Defines the application's domain-specific exceptions and the global
 * {@code @RestControllerAdvice} that translates them into consistent HTTP error
 * responses (including validation failures). Provides a single place to shape
 * the API's error contract.</p>
 */
package com.vw.eacontext.exception;

