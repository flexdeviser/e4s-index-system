-- E4S Index System - PostgreSQL Schema
-- Partitioned bitmap storage for index data

-- Drop old flat table
DROP TABLE IF EXISTS e4s_index.meter_index;
DROP TABLE IF EXISTS e4s_index.reindex_status;

-- Create new partitioned bitmap table
CREATE TABLE IF NOT EXISTS e4s_index.meter_index_partitioned (
    id BIGSERIAL PRIMARY KEY,
    index_name VARCHAR(100) NOT NULL,
    entity_id BIGINT NOT NULL,
    granularity VARCHAR(10) NOT NULL,
    partition_num INT NOT NULL,
    bitmap_data BYTEA NOT NULL,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    
    UNIQUE(index_name, entity_id, granularity, partition_num)
);

-- Index for fast lookups
CREATE INDEX IF NOT EXISTS idx_meter_idx_part_lookup 
    ON e4s_index.meter_index_partitioned (index_name, entity_id, granularity, partition_num);

-- Index for entity queries
CREATE INDEX IF NOT EXISTS idx_meter_idx_part_entity 
    ON e4s_index.meter_index_partitioned (index_name, entity_id);
