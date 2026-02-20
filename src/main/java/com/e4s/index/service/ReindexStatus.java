package com.e4s.index.service;

import com.e4s.index.model.Granularity;

/**
 * Represents the status of a reindex operation.
 * 
 * @author E4S Team
 * @version 1.0.0
 */
public class ReindexStatus {

    public enum Status {
        NOT_STARTED,
        RUNNING,
        COMPLETED,
        FAILED
    }

    private String indexName;
    private Status status;
    private Granularity granularity;
    private Integer partition;
    private long totalRecords;
    private long processedRecords;
    private String errorMessage;
    private long startedAt;
    private Long completedAt;

    public ReindexStatus() {
    }

    public ReindexStatus(String indexName, Status status) {
        this.indexName = indexName;
        this.status = status;
        this.startedAt = System.currentTimeMillis();
    }

    public String getIndexName() {
        return indexName;
    }

    public void setIndexName(String indexName) {
        this.indexName = indexName;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public Granularity getGranularity() {
        return granularity;
    }

    public void setGranularity(Granularity granularity) {
        this.granularity = granularity;
    }

    public Integer getPartition() {
        return partition;
    }

    public void setPartition(Integer partition) {
        this.partition = partition;
    }

    public long getTotalRecords() {
        return totalRecords;
    }

    public void setTotalRecords(long totalRecords) {
        this.totalRecords = totalRecords;
    }

    public long getProcessedRecords() {
        return processedRecords;
    }

    public void setProcessedRecords(long processedRecords) {
        this.processedRecords = processedRecords;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public long getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(long startedAt) {
        this.startedAt = startedAt;
    }

    public Long getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Long completedAt) {
        this.completedAt = completedAt;
    }
}
