package com.e4s.index.controller;

import com.e4s.index.dto.CreateIndexRequest;
import com.e4s.index.dto.ExistsResponse;
import com.e4s.index.dto.IndexInfo;
import com.e4s.index.dto.MarkRequest;
import com.e4s.index.dto.NavigationResponse;
import com.e4s.index.dto.QueryRequest;
import com.e4s.index.model.Granularity;
import com.e4s.index.service.IndexService;
import com.e4s.index.service.IndexStats;
import com.e4s.index.util.TimeUtils;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

/**
 * REST Controller for the E4S Index System API.
 * 
 * <p>This controller provides endpoints for managing and querying time-series indexes:</p>
 * 
 * <h2>Index Management</h2>
 * <ul>
 *   <li>{@code POST /api/v1/index} - Create a new index</li>
 *   <li>{@code GET /api/v1/index} - List all indexes</li>
 *   <li>{@code GET /api/v1/index/{name}} - Get index information</li>
 *   <li>{@code DELETE /api/v1/index/{name}} - Delete an index</li>
 * </ul>
 * 
 * <h2>Query Operations</h2>
 * <ul>
 *   <li>{@code POST /api/v1/index/exists} - Check if data exists</li>
 *   <li>{@code POST /api/v1/index/prev} - Find previous data point</li>
 *   <li>{@code POST /api/v1/index/next} - Find next data point</li>
 * </ul>
 * 
 * <h2>Write Operations</h2>
 * <ul>
 *   <li>{@code POST /api/v1/index/mark} - Mark timestamps in index</li>
 * </ul>
 * 
 * @author E4S Team
 * @version 1.0.0
 */
@RestController
@RequestMapping("/api/v1/index")
public class IndexController {

    private final IndexService indexService;

    /**
     * Creates a new IndexController.
     *
     * @param indexService the index service
     */
    public IndexController(IndexService indexService) {
        this.indexService = indexService;
    }

    /**
     * Creates a new index.
     *
     * @param request the create index request
     * @return 200 OK on success
     */
    @PostMapping
    public ResponseEntity<Void> createIndex(@Valid @RequestBody CreateIndexRequest request) {
        indexService.createIndex(request.indexName());
        return ResponseEntity.ok().build();
    }

    /**
     * Lists all available indexes.
     *
     * @return list of index names
     */
    @GetMapping
    public ResponseEntity<List<String>> listIndexes() {
        return ResponseEntity.ok(indexService.listIndexes());
    }

    /**
     * Gets information about an index.
     *
     * @param indexName the index name
     * @return index information or 404 if not found
     */
    @GetMapping("/{indexName}")
    public ResponseEntity<IndexInfo> getIndex(@PathVariable String indexName) {
        if (!indexService.indexExists(indexName)) {
            return ResponseEntity.notFound().build();
        }
        IndexStats stats = indexService.getStats(indexName);
        return ResponseEntity.ok(IndexInfo.from(indexName, stats));
    }

    /**
     * Deletes an index.
     *
     * @param indexName the index name
     * @return 200 OK on success
     */
    @DeleteMapping("/{indexName}")
    public ResponseEntity<Void> deleteIndex(@PathVariable String indexName) {
        indexService.deleteIndex(indexName);
        return ResponseEntity.ok().build();
    }

    /**
     * Checks if data exists at a specific timestamp.
     *
     * @param request the query request
     * @return existence check response
     */
    @PostMapping("/exists")
    public ResponseEntity<ExistsResponse> exists(@Valid @RequestBody QueryRequest request) {
        int value = TimeUtils.toEpochValue(request.timestamp(), request.granularity().name());
        boolean exists = indexService.exists(
                request.indexName(),
                request.entityId(),
                request.granularity(),
                value
        );
        return ResponseEntity.ok(new ExistsResponse(
                request.indexName(),
                request.entityId(),
                request.granularity(),
                request.timestamp(),
                exists
        ));
    }

    /**
     * Finds the previous data point before a timestamp.
     *
     * @param request the query request
     * @return navigation response with previous timestamp
     */
    @PostMapping("/prev")
    public ResponseEntity<NavigationResponse> findPrev(@Valid @RequestBody QueryRequest request) {
        int value = TimeUtils.toEpochValue(request.timestamp(), request.granularity().name());
        Optional<Integer> result = indexService.findPrev(
                request.indexName(),
                request.entityId(),
                request.granularity(),
                value
        );
        Long resultMillis = result
                .map(v -> TimeUtils.epochValueToMillis(v, request.granularity().name()))
                .orElse(null);
        return ResponseEntity.ok(new NavigationResponse(
                request.indexName(),
                request.entityId(),
                request.granularity(),
                request.timestamp(),
                resultMillis
        ));
    }

    /**
     * Finds the next data point after a timestamp.
     *
     * @param request the query request
     * @return navigation response with next timestamp
     */
    @PostMapping("/next")
    public ResponseEntity<NavigationResponse> findNext(@Valid @RequestBody QueryRequest request) {
        int value = TimeUtils.toEpochValue(request.timestamp(), request.granularity().name());
        Optional<Integer> result = indexService.findNext(
                request.indexName(),
                request.entityId(),
                request.granularity(),
                value
        );
        Long resultMillis = result
                .map(v -> TimeUtils.epochValueToMillis(v, request.granularity().name()))
                .orElse(null);
        return ResponseEntity.ok(new NavigationResponse(
                request.indexName(),
                request.entityId(),
                request.granularity(),
                request.timestamp(),
                resultMillis
        ));
    }

    /**
     * Marks timestamps in an index.
     *
     * @param request the mark request with timestamps
     * @return 200 OK on success
     */
    @PostMapping("/mark")
    public ResponseEntity<Void> mark(@Valid @RequestBody MarkRequest request) {
        int[] values = request.timestamps().stream()
                .mapToInt(ts -> TimeUtils.toEpochValue(ts, request.granularity().name()))
                .toArray();
        indexService.markBatch(
                request.indexName(),
                request.entityId(),
                request.granularity(),
                values
        );
        return ResponseEntity.ok().build();
    }

    /**
     * Evicts an entity from the cache.
     *
     * @param indexName the index name
     * @param entityId the entity ID
     * @return 200 OK on success
     */
    @DeleteMapping("/{indexName}/entity/{entityId}")
    public ResponseEntity<Void> evictEntity(
            @PathVariable String indexName,
            @PathVariable Long entityId) {
        indexService.evictEntity(indexName, entityId);
        return ResponseEntity.ok().build();
    }

    /**
     * Evicts an entire index from the cache.
     *
     * @param indexName the index name
     * @return 200 OK on success
     */
    @DeleteMapping("/{indexName}/cache")
    public ResponseEntity<Void> evictIndex(@PathVariable String indexName) {
        indexService.evictIndex(indexName);
        return ResponseEntity.ok().build();
    }
}
