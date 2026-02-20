package com.e4s.index.service;

/**
 * Statistics record for an index.
 * 
 * <p>Contains information about the current state of an index including:</p>
 * <ul>
 *   <li>Number of entities tracked</li>
 *   <li>Current cache size</li>
 *   <li>Memory usage</li>
 * </ul>
 *
 * @param entityCount the number of entities in the index
 * @param cacheSize the number of entries currently in the memory cache
 * @param memoryUsageBytes the memory usage in bytes
 * @author E4S Team
 * @version 1.0.0
 */
public record IndexStats(
        long entityCount,
        int cacheSize,
        long memoryUsageBytes
) {
}
