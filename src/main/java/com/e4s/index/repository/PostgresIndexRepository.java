package com.e4s.index.repository;

import com.e4s.index.config.IndexProperties;
import com.e4s.index.model.Granularity;
import com.e4s.index.model.MeterIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;

/**
 * PostgreSQL implementation of IndexRepository.
 * 
 * <p>Uses JDBC batch operations for efficient bulk inserts.</p>
 * 
 * @author E4S Team
 * @version 1.0.0
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
        String sql = String.format(
                "INSERT INTO %s.meter_index (index_name, entity_id, granularity, epoch_value, partition_num) " +
                "VALUES (?, ?, ?, ?, ?) " +
                "ON CONFLICT (index_name, entity_id, granularity, epoch_value) DO NOTHING",
                schema);

        jdbcTemplate.update(sql,
                index.getIndexName(),
                index.getEntityId(),
                index.getGranularity().name(),
                index.getEpochValue(),
                index.getPartitionNum());
    }

    @Override
    public void saveBatch(List<MeterIndex> indexes) {
        if (indexes.isEmpty()) {
            return;
        }

        String sql = String.format(
                "INSERT INTO %s.meter_index (index_name, entity_id, granularity, epoch_value, partition_num) " +
                "VALUES (?, ?, ?, ?, ?) " +
                "ON CONFLICT (index_name, entity_id, granularity, epoch_value) DO NOTHING",
                schema);

        int[][] batchResults = jdbcTemplate.batchUpdate(sql, indexes, batchSize, (ps, index) -> {
            ps.setString(1, index.getIndexName());
            ps.setLong(2, index.getEntityId());
            ps.setString(3, index.getGranularity().name());
            ps.setInt(4, index.getEpochValue());
            ps.setInt(5, index.getPartitionNum());
        });

        int inserted = 0;
        for (int[] batch : batchResults) {
            inserted += batch.length;
        }
        log.debug("Batch saved {} index entries", inserted);
    }

    @Override
    public boolean exists(String indexName, Long entityId, Granularity granularity, int epochValue) {
        String sql = String.format(
                "SELECT 1 FROM %s.meter_index " +
                "WHERE index_name = ? AND entity_id = ? AND granularity = ? AND epoch_value = ? " +
                "LIMIT 1",
                schema);

        return jdbcTemplate.query(sql, (rs, rowNum) -> rs.getInt(1),
                indexName, entityId, granularity.name(), epochValue).size() > 0;
    }

    @Override
    public List<Integer> findEpochValues(String indexName, Long entityId, Granularity granularity) {
        String sql = String.format(
                "SELECT epoch_value FROM %s.meter_index " +
                "WHERE index_name = ? AND entity_id = ? AND granularity = ? " +
                "ORDER BY epoch_value",
                schema);

        return jdbcTemplate.query(sql, epochValueMapper, indexName, entityId, granularity.name());
    }

    @Override
    public List<Integer> findEpochValuesByPartition(String indexName, Long entityId, 
                                                      Granularity granularity, int partition) {
        String sql = String.format(
                "SELECT epoch_value FROM %s.meter_index " +
                "WHERE index_name = ? AND entity_id = ? AND granularity = ? AND partition_num = ? " +
                "ORDER BY epoch_value",
                schema);

        return jdbcTemplate.query(sql, epochValueMapper, 
                indexName, entityId, granularity.name(), partition);
    }

    @Override
    public List<Long> findEntityIds(String indexName) {
        String sql = String.format(
                "SELECT DISTINCT entity_id FROM %s.meter_index " +
                "WHERE index_name = ? ORDER BY entity_id",
                schema);

        return jdbcTemplate.query(sql, (rs, rowNum) -> rs.getLong("entity_id"), indexName);
    }

    @Override
    public void deleteByIndexName(String indexName) {
        String sql = String.format(
                "DELETE FROM %s.meter_index WHERE index_name = ?",
                schema);

        int deleted = jdbcTemplate.update(sql, indexName);
        log.info("Deleted {} index entries for index '{}'", deleted, indexName);
    }

    @Override
    public long countByIndexName(String indexName) {
        String sql = String.format(
                "SELECT COUNT(*) FROM %s.meter_index WHERE index_name = ?",
                schema);

        return jdbcTemplate.queryForObject(sql, Long.class, indexName);
    }

    @Override
    public long countDistinctEntities(String indexName) {
        String sql = String.format(
                "SELECT COUNT(DISTINCT entity_id) FROM %s.meter_index WHERE index_name = ?",
                schema);

        return jdbcTemplate.queryForObject(sql, Long.class, indexName);
    }
}
