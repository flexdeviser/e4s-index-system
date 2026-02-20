package com.e4s.index.dto;

import java.time.Instant;

/**
 * Response DTO for error responses.
 *
 * @param timestamp when the error occurred
 * @param status the HTTP status code
 * @param error the error type
 * @param message the error message
 * @param path the request path
 * @author E4S Team
 * @version 1.0.0
 */
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path
) {
}
