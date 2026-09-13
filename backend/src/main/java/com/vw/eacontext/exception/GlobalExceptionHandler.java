package com.vw.eacontext.exception;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import com.vw.eacontext.dto.ApiError;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;

/**
 * Translates exceptions into consistent, structured JSON {@link ApiError}
 * responses (timestamp, status, error, message, details) with appropriate HTTP
 * status codes.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** No dataset loaded yet -> 409 Conflict. */
    @ExceptionHandler(ModelNotLoadedException.class)
    public ResponseEntity<ApiError> handleNotLoaded(ModelNotLoadedException ex) {
        return build(HttpStatus.CONFLICT, ex.getMessage());
    }

    /** Unknown entity -> 404 Not Found. */
    @ExceptionHandler(EaNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(EaNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    /** Parsing / ingestion problems -> 400 Bad Request. */
    @ExceptionHandler(EaIngestionException.class)
    public ResponseEntity<ApiError> handleIngestion(EaIngestionException ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /** Upload larger than the configured multipart limit -> 413 Payload Too Large. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleUploadTooLarge(MaxUploadSizeExceededException ex) {
        return build(HttpStatus.PAYLOAD_TOO_LARGE,
                "Uploaded file exceeds the maximum allowed size. Split the dataset or contact support "
                        + "to raise the limit.");
    }

    /** Bean validation on request bodies -> 400 with per-field details. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        List<String> details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .toList();
        return build(HttpStatus.BAD_REQUEST, "Validation failed", details);
    }

    /** Constraint violations on params/path vars -> 400 with details. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex) {
        List<String> details = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .toList();
        return build(HttpStatus.BAD_REQUEST, "Validation failed", details);
    }

    /** Other bad request conditions -> 400 Bad Request. */
    @ExceptionHandler({
            IllegalArgumentException.class,
            MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class
    })
    public ResponseEntity<ApiError> handleBadRequest(Exception ex) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    /** Anything else -> 500 Internal Server Error. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGeneric(Exception ex) {
        log.error("Unhandled exception", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error: " + ex.getMessage());
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String message) {
        return build(status, message, List.of());
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String message, List<String> details) {
        return ResponseEntity.status(status)
                .body(ApiError.of(status.value(), status.getReasonPhrase(), message, details));
    }
}

