package com.e4s.index.repository;

import com.e4s.index.model.Granularity;
import com.e4s.index.model.MeterIndex;

import java.util.List;

/**
 * Repository interface for Index persistence in PostgreSQL.
 * 
 * <p>This is the source of truth for index data. Redis serves as a hot cache
 * that can be rebuilt from this data.</p>
 * 
 * @author E4S Team
 * @version 1.0.0
 */
public interface IndexRepository {

    /**
     * Saves a single index entry.
     *
     * @param index the index entry to save
     */
    void save(MeterIndex index);

    /**
     * Saves multiple index entries in a batch.
     *
     * @param indexes the index entries to save
     */
    void saveBatch(List<MeterIndex> indexes);

    /**
     * Checks if an index entry exists.
     *
     * @param indexName the index name
     * @param entityId the entity ID
     * @param granularity the granularity
     * @param epochValue the epoch value
     * @return true if exists
     */
    boolean exists(String indexName, Long entityId, Granularity granularity, int epochValue);

    /**
     * Finds all epoch values for an entity and granularity.
     *
     * @param indexName the index name
     * @param entityId the entity ID
     * @param granularity the granularity
     * @return list of epoch values
     */
    List<Integer> findEpochValues(String indexName, Long entityId, Granularity granularity);

    /**
     * Finds all epoch values for an entity, granularity, and partition.
     *
     * @param indexName the index name
     * @param entityId the entity ID
     * @param granularity the granularity
     * @param partition the partition number
     * @return list of epoch values
     */
    List<Integer> findEpochValuesByPartition(String indexName, Long entityId, 
                                            Granularity granularity, int partition);

    /**
     * Finds all distinct entity IDs for an index.
     *
     * @param indexName the index name
     * @return list of entity IDs
     */
    List<Long> findEntityIds(String indexName);

    /**
     * Deletes all entries for an index.
     *
     * @param indexName the index name to delete
     */
    void deleteByIndexName(String indexName);

    /**
     * Counts total records for an index.
     *
     * @param indexName the index name
     * @return total record count
     */
    long countByIndexName(String indexName);

    /**
     * Counts distinct entities for an index.
     *
     * @param indexName the index name
     * @return distinct entity count
     */
    long countDistinctEntities(String indexName);

    // ===== Partitioned Bitmap Methods =====

    /**
     * Saves or updates a partition bitmap.
     * Reads existing bitmap, merges with new data, and saves.
     *
     * @param indexName the index name
     * @param entityId the entity ID
     * @param granularity the granularity
     * @param partitionNum the partition number
     * @param bitmapData the serialized RoaringBitmap data
     */
    void savePartitionBitmap(String indexName, Long entityId, Granularity granularity, 
                            int partitionNum, byte[] bitmapData);

    /**
     * Gets the partition bitmap data.
     *
     * @param indexName the index name
     * @param entityId the entity ID
     * @param granularity the granularity
     * @param partitionNum the partition number
     * @return serialized bitmap data or null if not exists
     */
    byte[] getPartitionBitmap(String indexName, Long entityId, Granularity granularity, 
                             int partitionNum);

    /**
     * Deletes a partition bitmap.
     *
     * @param indexName the index name
     * @param entityId the entity ID
     * @param granularity the granularity
     * @param partitionNum the partition number
     */
    void deletePartitionBitmap(String indexName, Long entityId, Granularity granularity, 
                               int partitionNum);
}
