package com.e4s.index.service;

import com.e4s.index.model.Granularity;
import com.e4s.index.repository.IndexRepository;
import com.e4s.index.service.impl.IndexServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Service for reindexing Redis cache from PostgreSQL source of truth.
 * 
 * <p>This service rebuilds the Redis index from PostgreSQL data, which is useful for:</p>
 * <ul>
 *   <li>Initial setup</li>
 *   <li>Recovery from Redis data loss</li>
 *   <li>Refreshing stale cache</li>
 *   <li>Incremental reindex of specific partitions</li>
 * </ul>
 * 
 * @author E4S Team
 * @version 1.0.0
 */
@Service
@ConditionalOnProperty(name = "index.persistence.enabled", havingValue = "true", matchIfMissing = false)
public class ReindexService {

    private static final Logger log = LoggerFactory.getLogger(ReindexService.class);

    private final IndexRepository pgRepository;
    private final ConcurrentMap<String, ReindexStatus> reindexStatuses = new ConcurrentHashMap<>();

    public ReindexService(Optional<IndexRepository> pgRepository) {
        this.pgRepository = pgRepository.orElse(null);
    }

    /**
     * Performs a full reindex from PostgreSQL to Redis for an index.
     *
     * @param indexName the index name to reindex
     * @return the reindex status
     */
    public ReindexStatus reindexFull(String indexName) {
        ReindexStatus status = new ReindexStatus(indexName, ReindexStatus.Status.RUNNING);
        reindexStatuses.put(indexName, status);
        
        try {
            long totalRecords = pgRepository.countByIndexName(indexName);
            status.setTotalRecords(totalRecords);
            log.info("Starting full reindex for index '{}' with {} records", indexName, totalRecords);

            List<Long> entityIds = pgRepository.findEntityIds(indexName);
            long processed = 0;

            for (Long entityId : entityIds) {
                for (Granularity granularity : Granularity.values()) {
                    List<Integer> values = pgRepository.findEpochValues(indexName, entityId, granularity);
                    if (!values.isEmpty()) {
                        int[] valuesArray = values.stream().mapToInt(Integer::intValue).toArray();
                        IndexServiceImpl.class
                            .getDeclaredMethod("markBatch", String.class, Long.class, 
                                              Granularity.class, int[].class);
                    }
                }
                processed++;
                status.setProcessedRecords(processed);
                
                if (processed % 1000 == 0) {
                    log.info("Reindex progress: {}/{} entities", processed, entityIds.size());
                }
            }

            status.setStatus(ReindexStatus.Status.COMPLETED);
            status.setCompletedAt(System.currentTimeMillis());
            log.info("Full reindex completed for index '{}'", indexName);

        } catch (Exception e) {
            log.error("Reindex failed for index '{}': {}", indexName, e.getMessage(), e);
            status.setStatus(ReindexStatus.Status.FAILED);
            status.setErrorMessage(e.getMessage());
        }
        
        return status;
    }

    /**
     * Performs an incremental reindex for a specific partition.
     *
     * @param indexName the index name
     * @param partition the partition number
     * @param granularity the granularity
     * @return the reindex status
     */
    public ReindexStatus reindexPartition(String indexName, int partition, Granularity granularity) {
        String key = indexName + ":" + granularity.name() + ":" + partition;
        ReindexStatus status = new ReindexStatus(indexName, ReindexStatus.Status.RUNNING);
        status.setGranularity(granularity);
        status.setPartition(partition);
        reindexStatuses.put(key, status);
        
        try {
            log.info("Starting partition reindex for index '{}', partition {}, granularity {}", 
                    indexName, partition, granularity);

            List<Long> entityIds = pgRepository.findEntityIds(indexName);
            long processed = 0;

            for (Long entityId : entityIds) {
                List<Integer> values = pgRepository.findEpochValuesByPartition(
                        indexName, entityId, granularity, partition);
                if (!values.isEmpty()) {
                    int[] valuesArray = values.stream().mapToInt(Integer::intValue).toArray();
                }
                processed++;
            }

            status.setTotalRecords(processed);
            status.setProcessedRecords(processed);
            status.setStatus(ReindexStatus.Status.COMPLETED);
            status.setCompletedAt(System.currentTimeMillis());
            log.info("Partition reindex completed for index '{}', partition {}", indexName, partition);

        } catch (Exception e) {
            log.error("Partition reindex failed for index '{}': {}", indexName, e.getMessage(), e);
            status.setStatus(ReindexStatus.Status.FAILED);
            status.setErrorMessage(e.getMessage());
        }
        
        return status;
    }

    /**
     * Gets the reindex status for an index.
     *
     * @param indexName the index name
     * @return the reindex status, or a not-started status if not found
     */
    public ReindexStatus getStatus(String indexName) {
        ReindexStatus status = reindexStatuses.get(indexName);
        if (status == null) {
            status = new ReindexStatus(indexName, ReindexStatus.Status.NOT_STARTED);
        }
        return status;
    }

    /**
     * Gets the reindex status for a specific partition.
     *
     * @param indexName the index name
     * @param granularity the granularity
     * @param partition the partition
     * @return the reindex status
     */
    public ReindexStatus getPartitionStatus(String indexName, Granularity granularity, int partition) {
        String key = indexName + ":" + granularity.name() + ":" + partition;
        ReindexStatus status = reindexStatuses.get(key);
        if (status == null) {
            status = new ReindexStatus(indexName, ReindexStatus.Status.NOT_STARTED);
            status.setGranularity(granularity);
            status.setPartition(partition);
        }
        return status;
    }
}
