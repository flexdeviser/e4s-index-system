package com.e4s.index.model;

import java.time.LocalDateTime;

/**
 * Entity representing a partitioned bitmap stored in PostgreSQL.
 * 
 * <p>Each row stores a serialized RoaringBitmap for a specific partition.
 * This is the source of truth for index data, with Redis serving as a hot cache.</p>
 */
public class PartitionedIndex {

    private Long id;
    private String indexName;
    private Long entityId;
    private Granularity granularity;
    private int partitionNum;
    private byte[] bitmapData;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public PartitionedIndex() {
    }

    public PartitionedIndex(String indexName, Long entityId, Granularity granularity, 
                           int partitionNum, byte[] bitmapData) {
        this.indexName = indexName;
        this.entityId = entityId;
        this.granularity = granularity;
        this.partitionNum = partitionNum;
        this.bitmapData = bitmapData;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getIndexName() {
        return indexName;
    }

    public void setIndexName(String indexName) {
        this.indexName = indexName;
    }

    public Long getEntityId() {
        return entityId;
    }

    public void setEntityId(Long entityId) {
        this.entityId = entityId;
    }

    public Granularity getGranularity() {
        return granularity;
    }

    public void setGranularity(Granularity granularity) {
        this.granularity = granularity;
    }

    public int getPartitionNum() {
        return partitionNum;
    }

    public void setPartitionNum(int partitionNum) {
        this.partitionNum = partitionNum;
    }

    public byte[] getBitmapData() {
        return bitmapData;
    }

    public void setBitmapData(byte[] bitmapData) {
        this.bitmapData = bitmapData;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
