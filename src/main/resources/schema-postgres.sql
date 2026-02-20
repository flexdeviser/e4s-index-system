-- E4S Index System - PostgreSQL Schema
-- Source of truth for index data

-- Create e4s_index schema
CREATE SCHEMA IF NOT EXISTS e4s_index;

-- Index data table
CREATE TABLE IF NOT EXISTS e4s_index.meter_index (
    id BIGSERIAL PRIMARY KEY,
    index_name VARCHAR(100) NOT NULL,
    entity_id BIGINT NOT NULL,
    granularity VARCHAR(10) NOT NULL,
    epoch_value INT NOT NULL,
    partition_num INT NOT NULL,
    created_at TIMESTAMP DEFAULT NOW(),
    
    UNIQUE(index_name, entity_id, granularity, epoch_value)
);

-- Index for fast lookups
CREATE INDEX IF NOT EXISTS idx_meter_index_lookup 
    ON e4s_index.meter_index (index_name, entity_id, granularity, epoch_value);

-- Index for partition-based queries (reindex)
CREATE INDEX IF NOT EXISTS idx_meter_index_partition 
    ON e4s_index.meter_index (index_name, entity_id, granularity, partition_num);

-- Index for entity queries
CREATE INDEX IF NOT EXISTS idx_meter_index_entity 
    ON e4s_index.meter_index (index_name, entity_id);

-- Re-index status tracking
CREATE TABLE IF NOT EXISTS e4s_index.reindex_status (
    id BIGSERIAL PRIMARY KEY,
    index_name VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL,
    granularity VARCHAR(10),
    partition_num INT,
    total_records BIGINT,
    processed_records BIGINT DEFAULT 0,
    started_at TIMESTAMP DEFAULT NOW(),
    completed_at TIMESTAMP,
    error_message TEXT,
    
    UNIQUE(index_name, status)
);
