package com.e4s.index.dto;

import com.e4s.index.model.Granularity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Request DTO for querying an index.
 *
 * @param indexName the index name
 * @param entityId the entity identifier
 * @param granularity the time granularity
 * @param timestamp the epoch milliseconds timestamp
 * @author E4S Team
 * @version 1.0.0
 */
public record QueryRequest(
        @NotBlank(message = "indexName is required")
        String indexName,
        
        @NotNull(message = "entityId is required")
        Long entityId,
        
        @NotNull(message = "granularity is required")
        Granularity granularity,
        
        @NotNull(message = "timestamp is required")
        @Positive(message = "timestamp must be positive")
        Long timestamp
) {
}
