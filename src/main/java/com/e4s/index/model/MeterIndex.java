package com.e4s.index.model;

import java.time.LocalDateTime;

/**
 * Entity representing a single index entry stored in PostgreSQL.
 * 
 * <p>This is the source of truth for index data. Redis serves as a hot cache
 * that can be rebuilt from this data.</p>
 * 
 * @author E4S Team
 * @version 1.0.0
 */
public class MeterIndex {

    private Long id;
    private String indexName;
    private Long entityId;
    private Granularity granularity;
    private int epochValue;
    private int partitionNum;
    private LocalDateTime createdAt;

    /**
     * Creates a new MeterIndex entry.
     *
     * @param indexName the index name
     * @param entityId the entity ID
     * @param granularity the granularity
     * @param epochValue the epoch value
     * @param partitionNum the partition number
     */
    public MeterIndex(String indexName, Long entityId, Granularity granularity, 
                      int epochValue, int partitionNum) {
        this.indexName = indexName;
        this.entityId = entityId;
        this.granularity = granularity;
        this.epochValue = epochValue;
        this.partitionNum = partitionNum;
        this.createdAt = LocalDateTime.now();
    }

    /**
     * Default constructor for JdbcTemplate.
     */
    public MeterIndex() {
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

    public int getEpochValue() {
        return epochValue;
    }

    public void setEpochValue(int epochValue) {
        this.epochValue = epochValue;
    }

    public int getPartitionNum() {
        return partitionNum;
    }

    public void setPartitionNum(int partitionNum) {
        this.partitionNum = partitionNum;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public String toString() {
        return "MeterIndex{" +
                "indexName='" + indexName + '\'' +
                ", entityId=" + entityId +
                ", granularity=" + granularity +
                ", epochValue=" + epochValue +
                ", partitionNum=" + partitionNum +
                '}';
    }
}
