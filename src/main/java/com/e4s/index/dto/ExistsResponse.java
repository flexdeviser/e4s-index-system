package com.e4s.index.dto;

import com.e4s.index.model.Granularity;

/**
 * Response DTO for existence check operations.
 *
 * @param indexName the index name
 * @param entityId the entity identifier
 * @param granularity the time granularity
 * @param timestamp the queried timestamp
 * @param exists whether data exists at the timestamp
 * @author E4S Team
 * @version 1.0.0
 */
public record ExistsResponse(
        String indexName,
        Long entityId,
        Granularity granularity,
        Long timestamp,
        boolean exists
) {
}
