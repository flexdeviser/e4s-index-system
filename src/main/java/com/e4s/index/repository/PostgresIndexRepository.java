package com.e4s.index.repository;

import com.e4s.index.config.IndexProperties;
import com.e4s.index.model.Granularity;
import com.e4s.index.model.MeterIndex;
import com.e4s.index.model.PartitionedIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;

/**
 * PostgreSQL implementation of IndexRepository.
 * 
 * <p>Uses partitioned bitmap storage for efficient space usage.
 * Each partition (180 days for DAY granularity) stores a serialized RoaringBitmap.</p>
 */
public class PostgresIndexRepository implements IndexRepository {

    private static final Logger log = LoggerFactory.getLogger(PostgresIndexRepository.class);

    private final JdbcTemplate jdbcTemplate;
    private final String schema;
    private final int batchSize;

    private final RowMapper<Integer> epochValueMapper = (rs, rowNum) -> rs.getInt("epoch_value");

    public PostgresIndexRepository(JdbcTemplate jdbcTemplate, IndexProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.schema = properties.getPersistence().getSchema();
        this.batchSize = properties.getPersistence().getBatchSize();
    }

    @Override
    public void save(MeterIndex index) {
        throw new UnsupportedOperationException("Use savePartitionBitmap instead");
    }

    @Override
    public void saveBatch(List<MeterIndex> indexes) {
        if (indexes.isEmpty()) {
            return;
        }
        for (MeterIndex index : indexes) {
            save(index);
        }
    }

    @Override
    public boolean exists(String indexName, Long entityId, Granularity granularity, int epochValue) {
        int partition = getPartitionForEpochValue(epochValue, granularity);
        byte[] bitmap = getPartitionBitmap(indexName, entityId, granularity, partition);
        
        if (bitmap == null || bitmap.length == 0) {
            return false;
        }
        
        try {
            com.e4s.index.model.TimeIndex timeIndex = com.e4s.index.model.TimeIndex.deserialize(bitmap);
            int relativeValue = getPartitionValueForEpoch(epochValue, granularity);
            return timeIndex.contains(relativeValue);
        } catch (Exception e) {
            log.error("Failed to deserialize bitmap for exists check: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public List<Integer> findEpochValues(String indexName, Long entityId, Granularity granularity) {
        String sql = String.format(
                "SELECT partition_num FROM %s.meter_index_partitioned " +
                "WHERE index_name = ? AND entity_id = ? AND granularity = ?",
                schema);

        List<Integer> partitions = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getInt("partition_num"),
                indexName, entityId, granularity.name());

        java.util.Set<Integer> allEpochValues = new java.util.TreeSet<>();
        
        int partitionSize = getPartitionSize(granularity);
        
        for (int partition : partitions) {
            byte[] bitmap = getPartitionBitmap(indexName, entityId, granularity, partition);
            if (bitmap != null && bitmap.length > 0) {
                try {
                    com.e4s.index.model.TimeIndex timeIndex = com.e4s.index.model.TimeIndex.deserialize(bitmap);
                    int startValue = partition * partitionSize;
                    for (int value : timeIndex.toArray()) {
                        allEpochValues.add(startValue + value);
                    }
                } catch (Exception e) {
                    log.error("Failed to deserialize bitmap: {}", e.getMessage());
                }
            }
        }
        
        return new java.util.ArrayList<>(allEpochValues);
    }

    @Override
    public List<Integer> findEpochValuesByPartition(String indexName, Long entityId, 
                                                    Granularity granularity, int partition) {
        byte[] bitmap = getPartitionBitmap(indexName, entityId, granularity, partition);
        
        if (bitmap == null || bitmap.length == 0) {
            return List.of();
        }
        
        try {
            com.e4s.index.model.TimeIndex timeIndex = com.e4s.index.model.TimeIndex.deserialize(bitmap);
            int partitionSize = getPartitionSize(granularity);
            int startValue = partition * partitionSize;
            java.util.List<Integer> result = new java.util.ArrayList<>();
            for (int value : timeIndex.toArray()) {
                result.add(startValue + value);
            }
            return result;
        } catch (Exception e) {
            log.error("Failed to deserialize bitmap: {}", e.getMessage());
            return List.of();
        }
    }

    @Override
    public List<Long> findEntityIds(String indexName) {
        String sql = String.format(
                "SELECT DISTINCT entity_id FROM %s.meter_index_partitioned " +
                "WHERE index_name = ? ORDER BY entity_id",
                schema);

        return jdbcTemplate.query(sql, (rs, rowNum) -> rs.getLong("entity_id"), indexName);
    }

    @Override
    public void deleteByIndexName(String indexName) {
        String sql = String.format(
                "DELETE FROM %s.meter_index_partitioned WHERE index_name = ?",
                schema);

        int deleted = jdbcTemplate.update(sql, indexName);
        log.info("Deleted {} partition entries for index '{}'", deleted, indexName);
    }

    @Override
    public long countByIndexName(String indexName) {
        String sql = String.format(
                "SELECT COUNT(*) FROM %s.meter_index_partitioned WHERE index_name = ?",
                schema);

        return jdbcTemplate.queryForObject(sql, Long.class, indexName);
    }

    @Override
    public long countDistinctEntities(String indexName) {
        String sql = String.format(
                "SELECT COUNT(DISTINCT entity_id) FROM %s.meter_index_partitioned WHERE index_name = ?",
                schema);

        return jdbcTemplate.queryForObject(sql, Long.class, indexName);
    }

    // ===== Partitioned Bitmap Methods =====

    @Override
    public void savePartitionBitmap(String indexName, Long entityId, Granularity granularity, 
                                    int partitionNum, byte[] bitmapData) {
        if (bitmapData == null || bitmapData.length == 0) {
            return;
        }

        String sql = String.format(
                "INSERT INTO %s.meter_index_partitioned " +
                "(index_name, entity_id, granularity, partition_num, bitmap_data) " +
                "VALUES (?, ?, ?, ?, ?) " +
                "ON CONFLICT (index_name, entity_id, granularity, partition_num) " +
                "DO UPDATE SET bitmap_data = EXCLUDED.bitmap_data, updated_at = NOW()",
                schema);

        jdbcTemplate.update(sql,
                indexName,
                entityId,
                granularity.name(),
                partitionNum,
                bitmapData);
    }

    @Override
    public byte[] getPartitionBitmap(String indexName, Long entityId, Granularity granularity, 
                                     int partitionNum) {
        String sql = String.format(
                "SELECT bitmap_data FROM %s.meter_index_partitioned " +
                "WHERE index_name = ? AND entity_id = ? AND granularity = ? AND partition_num = ?",
                schema);

        List<byte[]> results = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getBytes("bitmap_data"),
                indexName, entityId, granularity.name(), partitionNum);

        return results.isEmpty() ? null : results.get(0);
    }

    @Override
    public void deletePartitionBitmap(String indexName, Long entityId, Granularity granularity, 
                                      int partitionNum) {
        String sql = String.format(
                "DELETE FROM %s.meter_index_partitioned " +
                "WHERE index_name = ? AND entity_id = ? AND granularity = ? AND partition_num = ?",
                schema);

        jdbcTemplate.update(sql, indexName, entityId, granularity.name(), partitionNum);
    }

    // ===== Helper Methods =====

    private int getPartitionForEpochValue(int epochValue, Granularity granularity) {
        return switch (granularity) {
            case DAY -> epochValue / 180;
            case MONTH -> epochValue / 6;
            case YEAR -> epochValue;
        };
    }

    private int getPartitionValueForEpoch(int epochValue, Granularity granularity) {
        return switch (granularity) {
            case DAY -> epochValue % 180;
            case MONTH -> epochValue % 6;
            case YEAR -> 0;
        };
    }

    private int getPartitionStartValue(int partition, Granularity granularity) {
        return switch (granularity) {
            case DAY -> partition * 180;
            case MONTH -> partition * 6;
            case YEAR -> partition;
        };
    }

    private int getPartitionSize(Granularity granularity) {
        return switch (granularity) {
            case DAY -> 180;
            case MONTH -> 6;
            case YEAR -> 1;
        };
    }
}
