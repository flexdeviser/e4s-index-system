package com.e4s.index.service;

import com.e4s.index.model.Granularity;
import com.e4s.index.model.TimeIndex;

import java.util.List;
import java.util.Optional;

/**
 * Service interface for managing time-series indexes.
 * 
 * <p>This service provides operations for:</p>
 * <ul>
 *   <li>Creating and managing named indexes</li>
 *   <li>Marking time periods for entities</li>
 *   <li>Querying data existence</li>
 *   <li>Navigating to previous/next time periods</li>
 *   <li>Managing the in-memory cache</li>
 * </ul>
 * 
 * <p>Each index is identified by a unique name and can track data
 * at multiple granularity levels (DAY, MONTH, YEAR).</p>
 * 
 * @author E4S Team
 * @version 1.0.0
 * @see Granularity
 * @see TimeIndex
 */
public interface IndexService {

    /**
     * Creates a new index with the specified name.
     *
     * @param indexName the unique name for the index
     */
    void createIndex(String indexName);

    /**
     * Checks if an index with the specified name exists.
     *
     * @param indexName the index name to check
     * @return true if the index exists, false otherwise
     */
    boolean indexExists(String indexName);

    /**
     * Deletes an index and all its associated data.
     *
     * @param indexName the name of the index to delete
     */
    void deleteIndex(String indexName);

    /**
     * Lists all available index names.
     *
     * @return a list of index names
     */
    List<String> listIndexes();

    /**
     * Marks a single time value for an entity.
     *
     * @param indexName the index name
     * @param entityId the entity identifier
     * @param granularity the time granularity
     * @param value the time value (epoch-based)
     */
    void mark(String indexName, Long entityId, Granularity granularity, int value);

    /**
     * Marks multiple time values for an entity.
     *
     * @param indexName the index name
     * @param entityId the entity identifier
     * @param granularity the time granularity
     * @param values the time values to mark (epoch-based)
     */
    void markBatch(String indexName, Long entityId, Granularity granularity, int[] values);

    /**
     * Checks if a time value exists for an entity.
     *
     * @param indexName the index name
     * @param entityId the entity identifier
     * @param granularity the time granularity
     * @param value the time value to check (epoch-based)
     * @return true if the value exists, false otherwise
     */
    boolean exists(String indexName, Long entityId, Granularity granularity, int value);

    /**
     * Finds the previous time value before the specified value.
     *
     * @param indexName the index name
     * @param entityId the entity identifier
     * @param granularity the time granularity
     * @param value the reference value (epoch-based)
     * @return an Optional containing the previous value, or empty if none exists
     */
    Optional<Integer> findPrev(String indexName, Long entityId, Granularity granularity, int value);

    /**
     * Finds the next time value after the specified value.
     *
     * @param indexName the index name
     * @param entityId the entity identifier
     * @param granularity the time granularity
     * @param value the reference value (epoch-based)
     * @return an Optional containing the next value, or empty if none exists
     */
    Optional<Integer> findNext(String indexName, Long entityId, Granularity granularity, int value);

    /**
     * Evicts an entity from the in-memory cache.
     *
     * @param indexName the index name
     * @param entityId the entity identifier
     */
    void evictEntity(String indexName, Long entityId);

    /**
     * Evicts all entities for an index from the in-memory cache.
     *
     * @param indexName the index name
     */
    void evictIndex(String indexName);

    /**
     * Returns the count of entities in an index.
     *
     * @param indexName the index name
     * @return the entity count
     */
    long entityCount(String indexName);

    /**
     * Returns statistics for an index.
     *
     * @param indexName the index name
     * @return the index statistics
     */
    IndexStats getStats(String indexName);
}
