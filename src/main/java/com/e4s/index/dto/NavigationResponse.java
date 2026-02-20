package com.e4s.index.dto;

import com.e4s.index.model.Granularity;

/**
 * Response DTO for navigation operations (prev/next).
 *
 * @param indexName the index name
 * @param entityId the entity identifier
 * @param granularity the time granularity
 * @param timestamp the queried timestamp
 * @param result the found timestamp (previous or next), or null if none exists
 * @author E4S Team
 * @version 1.0.0
 */
public record NavigationResponse(
        String indexName,
        Long entityId,
        Granularity granularity,
        Long timestamp,
        Long result
) {
}
