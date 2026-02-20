package com.e4s.index.dto;

import com.e4s.index.service.IndexStats;

/**
 * Response DTO for index information.
 *
 * @param name the index name
 * @param entityCount the number of entities in the index
 * @param cacheSize the current cache size
 * @param memoryUsageBytes the memory usage in bytes
 * @author E4S Team
 * @version 1.0.0
 */
public record IndexInfo(
        String name,
        long entityCount,
        int cacheSize,
        long memoryUsageBytes
) {
    /**
     * Creates an IndexInfo from an IndexStats.
     *
     * @param name the index name
     * @param stats the index statistics
     * @return the IndexInfo
     */
    public static IndexInfo from(String name, IndexStats stats) {
        return new IndexInfo(name, stats.entityCount(), stats.cacheSize(), stats.memoryUsageBytes());
    }
}
