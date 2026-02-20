package com.e4s.index.controller;

import com.e4s.index.model.Granularity;
import com.e4s.index.service.IndexService;
import com.e4s.index.service.ReindexService;
import com.e4s.index.service.ReindexStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Admin REST controller for reindex operations.
 * 
 * <p>These endpoints are typically used for:</p>
 * <ul>
 *   <li>Rebuilding Redis cache from PostgreSQL</li>
 *   <li>Checking reindex progress</li>
 *   <li>Managing index lifecycle</li>
 * </ul>
 * 
 * @author E4S Team
 * @version 1.0.0
 */
@RestController
@RequestMapping("/api/v1/admin/index")
@ConditionalOnProperty(name = "index.persistence.enabled", havingValue = "true", matchIfMissing = false)
public class AdminController {

    private final ReindexService reindexService;
    private final IndexService indexService;

    public AdminController(ReindexService reindexService, IndexService indexService) {
        this.reindexService = reindexService;
        this.indexService = indexService;
    }

    /**
     * Triggers a full reindex for an index.
     *
     * @param indexName the index name
     * @return the reindex status
     */
    @PostMapping("/{indexName}/reindex")
    public ResponseEntity<ReindexStatus> reindexFull(@PathVariable String indexName) {
        if (!indexService.indexExists(indexName)) {
            return ResponseEntity.notFound().build();
        }
        ReindexStatus status = reindexService.reindexFull(indexName);
        return ResponseEntity.ok(status);
    }

    /**
     * Triggers an incremental reindex for a specific partition.
     *
     * @param indexName the index name
     * @param partition the partition number
     * @param granularity the granularity (DAY, MONTH, YEAR)
     * @return the reindex status
     */
    @PostMapping("/{indexName}/reindex/partition")
    public ResponseEntity<ReindexStatus> reindexPartition(
            @PathVariable String indexName,
            @RequestParam int partition,
            @RequestParam(defaultValue = "DAY") Granularity granularity) {
        
        if (!indexService.indexExists(indexName)) {
            return ResponseEntity.notFound().build();
        }
        ReindexStatus status = reindexService.reindexPartition(indexName, partition, granularity);
        return ResponseEntity.ok(status);
    }

    /**
     * Gets the reindex status for an index.
     *
     * @param indexName the index name
     * @return the reindex status
     */
    @GetMapping("/{indexName}/reindex/status")
    public ResponseEntity<ReindexStatus> getReindexStatus(@PathVariable String indexName) {
        ReindexStatus status = reindexService.getStatus(indexName);
        return ResponseEntity.ok(status);
    }

    /**
     * Gets the reindex status for a specific partition.
     *
     * @param indexName the index name
     * @param partition the partition number
     * @param granularity the granularity
     * @return the reindex status
     */
    @GetMapping("/{indexName}/reindex/partition/status")
    public ResponseEntity<ReindexStatus> getPartitionReindexStatus(
            @PathVariable String indexName,
            @RequestParam int partition,
            @RequestParam(defaultValue = "DAY") Granularity granularity) {
        
        ReindexStatus status = reindexService.getPartitionStatus(indexName, granularity, partition);
        return ResponseEntity.ok(status);
    }
}
