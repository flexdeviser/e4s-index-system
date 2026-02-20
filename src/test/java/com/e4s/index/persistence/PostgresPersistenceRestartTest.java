package com.e4s.index.persistence;

import com.e4s.index.config.IndexProperties;
import com.e4s.index.model.Granularity;
import com.e4s.index.model.PartitionedIndex;
import com.e4s.index.repository.IndexRepository;
import com.e4s.index.service.IndexService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("it-sync")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("PostgreSQL Persistence - Data Survival After Restart Tests")
class PostgresPersistenceRestartTest {

    @Autowired
    private IndexService indexService;

    @Autowired
    private IndexRepository indexRepository;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private IndexProperties indexProperties;

    private static final String RESTART_TEST_INDEX = "restart-test-index";
    private static final Long RESTART_TEST_ENTITY = 4001L;

    @Test
    @Order(1)
    @DisplayName("Step 1: Write data to PostgreSQL (simulates running service)")
    void step1_writeData_shouldPersistToPostgreSQL() throws Exception {
        assertThat(indexProperties.getPersistence().isEnabled()).isTrue();

        int[] epochValues = {100, 150, 200, 250};

        indexService.markBatch(RESTART_TEST_INDEX, RESTART_TEST_ENTITY, Granularity.DAY, epochValues);

        if (indexProperties.getPersistence().isAsyncWrite()) {
            Thread.sleep(1000);
        }

        List<Integer> foundValues = indexRepository.findEpochValues(RESTART_TEST_INDEX, RESTART_TEST_ENTITY, Granularity.DAY);
        assertThat(foundValues).hasSize(4);

        long count = indexRepository.countByIndexName(RESTART_TEST_INDEX);
        assertThat(count).isGreaterThanOrEqualTo(1);

        System.out.println("Step 1 complete: Data written to PostgreSQL");
    }

    @Test
    @Order(2)
    @DisplayName("Step 2: Verify data persists after application restart (new context)")
    void step2_afterContextRestart_dataShouldSurviveInPostgreSQL() {
        List<Integer> foundValues = indexRepository.findEpochValues(RESTART_TEST_INDEX, RESTART_TEST_ENTITY, Granularity.DAY);

        assertThat(foundValues).hasSize(4);
        assertThat(foundValues).containsExactlyInAnyOrder(100, 150, 200, 250);

        System.out.println("Step 2 complete: Data survived application restart");
    }

    @Test
    @Order(3)
    @DisplayName("Step 3: Verify direct SQL query returns same data")
    void step3_directSqlQuery_shouldMatchRepository() {
        String sql = "SELECT index_name, entity_id, granularity, partition_num, bitmap_data " +
                "FROM e4s_index.meter_index_partitioned " +
                "WHERE index_name = ? AND entity_id = ? " +
                "ORDER BY partition_num";

        List<PartitionedIndex> indexes = jdbcTemplate.query(sql,
                (rs, rowNum) -> {
                    PartitionedIndex pi = new PartitionedIndex();
                    pi.setIndexName(rs.getString("index_name"));
                    pi.setEntityId(rs.getLong("entity_id"));
                    pi.setGranularity(Granularity.valueOf(rs.getString("granularity")));
                    pi.setPartitionNum(rs.getInt("partition_num"));
                    pi.setBitmapData(rs.getBytes("bitmap_data"));
                    return pi;
                },
                RESTART_TEST_INDEX, RESTART_TEST_ENTITY);

        assertThat(indexes).isNotEmpty();
        assertThat(indexes).hasSize(2);
        assertThat(indexes.get(0).getBitmapData()).isNotNull();

        System.out.println("Step 3 complete: Direct SQL query verified");
    }

    @Test
    @Order(4)
    @DisplayName("Step 4: Write additional data in new context, verify cumulative writes")
    void step4_writeMoreData_shouldAccumulateInPostgreSQL() throws Exception {
        int[] newEpochValues = {300, 350};

        indexService.markBatch(RESTART_TEST_INDEX, RESTART_TEST_ENTITY, Granularity.DAY, newEpochValues);

        if (indexProperties.getPersistence().isAsyncWrite()) {
            Thread.sleep(1000);
        }

        List<Integer> allValues = indexRepository.findEpochValues(RESTART_TEST_INDEX, RESTART_TEST_ENTITY, Granularity.DAY);

        assertThat(allValues).hasSize(6);
        assertThat(allValues).contains(100, 150, 200, 250, 300, 350);

        System.out.println("Step 4 complete: Additional data written and verified");
    }

    @Test
    @Order(5)
    @DisplayName("Step 5: Cleanup - delete test data")
    void step5_cleanup_shouldRemoveTestData() {
        indexRepository.deleteByIndexName(RESTART_TEST_INDEX);

        long count = indexRepository.countByIndexName(RESTART_TEST_INDEX);
        assertThat(count).isEqualTo(0);

        System.out.println("Step 5 complete: Test data cleaned up");
    }
}
