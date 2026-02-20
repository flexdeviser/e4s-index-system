package com.e4s.index.util;

import com.e4s.index.model.Granularity;

/**
 * Utility class for partition calculations.
 * 
 * <p>Partitions the time index into fixed-size chunks to reduce memory usage.
 * Instead of holding a full year's bitmap in memory, we only hold ~180 days.</p>
 * 
 * <h2>Partition Size</h2>
 * <ul>
 *   <li>DAY: 180 days per partition</li>
 *   <li>MONTH: 6 months per partition (≈180 days)</li>
 *   <li>YEAR: 1 year per partition</li>
 * </ul>
 * 
 * <h2>Key Structure</h2>
 * <pre>
 * e4s:index:{name}:{granularity}:{entityId}:{partition}
 * Example: e4s:index:meter-data:day:12345:109
 * </pre>
 * 
 * @author E4S Team
 * @version 1.0.0
 */
public final class PartitionUtils {

    public static final int PARTITION_DAYS = 180;
    public static final int PARTITION_MONTHS = 6;

    private PartitionUtils() {
    }

    /**
     * Gets the partition number for a given epoch value and granularity.
     *
     * @param epochValue the epoch value (day, month, or year index)
     * @param granularity the granularity
     * @return the partition number
     */
    public static int getPartition(int epochValue, Granularity granularity) {
        return switch (granularity) {
            case DAY -> epochValue / PARTITION_DAYS;
            case MONTH -> epochValue / PARTITION_MONTHS;
            case YEAR -> epochValue;
        };
    }

    /**
     * Gets the start epoch value for a partition.
     *
     * @param partition the partition number
     * @param granularity the granularity
     * @return the start epoch value for that partition
     */
    public static int getPartitionStartValue(int partition, Granularity granularity) {
        return switch (granularity) {
            case DAY -> partition * PARTITION_DAYS;
            case MONTH -> partition * PARTITION_MONTHS;
            case YEAR -> partition;
        };
    }

    /**
     * Gets the end epoch value (exclusive) for a partition.
     *
     * @param partition the partition number
     * @param granularity the granularity
     * @return the end epoch value for that partition
     */
    public static int getPartitionEndValue(int partition, Granularity granularity) {
        return switch (granularity) {
            case DAY -> (partition + 1) * PARTITION_DAYS;
            case MONTH -> (partition + 1) * PARTITION_MONTHS;
            case YEAR -> partition + 1;
        };
    }

    /**
     * Gets the key for a specific entity, granularity, and partition.
     *
     * @param indexName the index name
     * @param granularity the granularity
     * @param entityId the entity ID
     * @param partition the partition number
     * @return the Redis key
     */
    public static String buildKey(String indexName, Granularity granularity, long entityId, int partition) {
        return String.format("e4s:index:%s:%s:%d:%d", 
                indexName, granularity.name().toLowerCase(), entityId, partition);
    }

    /**
     * Gets the partition key from an epoch value.
     *
     * @param indexName the index name
     * @param granularity the granularity
     * @param entityId the entity ID
     * @param epochValue the epoch value
     * @return the Redis key
     */
    public static String buildKeyForValue(String indexName, Granularity granularity, long entityId, int epochValue) {
        int partition = getPartition(epochValue, granularity);
        return buildKey(indexName, granularity, entityId, partition);
    }

    /**
     * Gets the key for the previous partition.
     *
     * @param indexName the index name
     * @param granularity the granularity
     * @param entityId the entity ID
     * @param epochValue the epoch value
     * @return the Redis key for the previous partition, or null if at partition 0
     */
    public static String getPrevPartitionKey(String indexName, Granularity granularity, long entityId, int epochValue) {
        int partition = getPartition(epochValue, granularity);
        if (partition <= 0) {
            return null;
        }
        return buildKey(indexName, granularity, entityId, partition - 1);
    }

    /**
     * Gets the key for the next partition.
     *
     * @param indexName the index name
     * @param granularity the granularity
     * @param entityId the entity ID
     * @param epochValue the epoch value
     * @return the Redis key for the next partition
     */
    public static String getNextPartitionKey(String indexName, Granularity granularity, long entityId, int epochValue) {
        int partition = getPartition(epochValue, granularity);
        return buildKey(indexName, granularity, entityId, partition + 1);
    }
}
