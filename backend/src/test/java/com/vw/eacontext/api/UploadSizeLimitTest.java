package com.vw.eacontext.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import com.vw.eacontext.dto.ApiError;
import com.vw.eacontext.exception.GlobalExceptionHandler;

/**
 * An upload past the configured multipart size limit must be a clean, structured
 * 413 (via {@link GlobalExceptionHandler}), not the generic 500 that
 * {@code MaxUploadSizeExceededException} previously fell into with no dedicated
 * handler for it.
 */
class UploadSizeLimitTest {

    @Test
    void oversizedUploadMapsToStructured413() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        ResponseEntity<ApiError> response =
                handler.handleUploadTooLarge(new MaxUploadSizeExceededException(25L * 1024 * 1024));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().status()).isEqualTo(413);
        assertThat(response.getBody().message()).isNotBlank();
    }
}
