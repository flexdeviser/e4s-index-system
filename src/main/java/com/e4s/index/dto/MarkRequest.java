package com.e4s.index.dto;

import com.e4s.index.model.Granularity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

/**
 * Request DTO for marking timestamps in an index.
 *
 * @param indexName the index name
 * @param entityId the entity identifier
 * @param granularity the time granularity
 * @param timestamps the list of epoch milliseconds timestamps to mark
 * @author E4S Team
 * @version 1.0.0
 */
public record MarkRequest(
        @NotBlank(message = "indexName is required")
        String indexName,
        
        @NotNull(message = "entityId is required")
        Long entityId,
        
        @NotNull(message = "granularity is required")
        Granularity granularity,
        
        @NotEmpty(message = "timestamps cannot be empty")
        List<@Positive(message = "timestamp must be positive") Long> timestamps
) {
}
