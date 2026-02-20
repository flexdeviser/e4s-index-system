package com.e4s.index.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Request DTO for creating a new index.
 *
 * @param indexName the unique name for the index (alphanumeric, underscore, or hyphen only)
 * @author E4S Team
 * @version 1.0.0
 */
public record CreateIndexRequest(
        @NotBlank(message = "indexName is required")
        @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "indexName must contain only alphanumeric, underscore, or hyphen")
        String indexName
) {
}
