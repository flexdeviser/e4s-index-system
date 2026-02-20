package com.e4s.index.persistence;

import com.e4s.index.config.IndexProperties;
import com.e4s.index.model.Granularity;
import com.e4s.index.repository.IndexRepository;
import com.e4s.index.service.IndexService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("it-sync")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@DisplayName("PostgreSQL Persistence - Synchronous Write Tests")
class PostgresPersistenceSyncTest {

    @Autowired
    private IndexService indexService;

    @Autowired
    private IndexRepository indexRepository;

    @Autowired
    private IndexProperties indexProperties;

    private static final String TEST_INDEX = "sync-test-index";
    private static final Long TEST_ENTITY_ID = 1001L;

    @BeforeEach
    void setUp() {
        assertThat(indexProperties.getPersistence().isEnabled()).isTrue();
        assertThat(indexProperties.getPersistence().isAsyncWrite()).isFalse();
        assertThat(indexRepository).isNotNull();
        indexRepository.deleteByIndexName(TEST_INDEX);
    }

    @AfterEach
    void cleanup() {
        indexRepository.deleteByIndexName(TEST_INDEX);
    }

    @Test
    @DisplayName("Single mark() should persist data synchronously to PostgreSQL")
    void mark_singleWrite_shouldPersistImmediately() {
        int epochValue = 1704067200;

        indexService.mark(TEST_INDEX, TEST_ENTITY_ID, Granularity.DAY, epochValue);

        boolean exists = indexRepository.exists(TEST_INDEX, TEST_ENTITY_ID, Granularity.DAY, epochValue);
        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("Batch markBatch() should persist data synchronously to PostgreSQL")
    void markBatch_batchWrite_shouldPersistImmediately() {
        int[] epochValues = {1704067200, 1704153600, 1704240000};

        indexService.markBatch(TEST_INDEX, TEST_ENTITY_ID, Granularity.DAY, epochValues);

        for (int epochValue : epochValues) {
            boolean exists = indexRepository.exists(TEST_INDEX, TEST_ENTITY_ID, Granularity.DAY, epochValue);
            assertThat(exists).isTrue();
        }
    }

    @Test
    @DisplayName("Data should be queryable by index name, entity id, and granularity")
    void findEpochValues_shouldReturnAllPersistedData() {
        int[] epochValues = {1704067200, 1704153600, 1704240000, 1704326400};

        indexService.markBatch(TEST_INDEX, TEST_ENTITY_ID, Granularity.DAY, epochValues);

        List<Integer> foundValues = indexRepository.findEpochValues(TEST_INDEX, TEST_ENTITY_ID, Granularity.DAY);

        assertThat(foundValues).hasSize(4);
        assertThat(foundValues).containsExactlyInAnyOrder(1704067200, 1704153600, 1704240000, 1704326400);
    }

    @Test
    @DisplayName("Data should be queryable by partition")
    void findEpochValuesByPartition_shouldReturnDataForSpecificPartition() {
        int partition0 = 0;
        int partition1 = 1;

        indexService.mark(TEST_INDEX, TEST_ENTITY_ID, Granularity.DAY, 100);
        indexService.mark(TEST_INDEX, TEST_ENTITY_ID, Granularity.DAY, 150);
        indexService.mark(TEST_INDEX, TEST_ENTITY_ID, Granularity.DAY, 200);

        List<Integer> partition0Values = indexRepository.findEpochValuesByPartition(
                TEST_INDEX, TEST_ENTITY_ID, Granularity.DAY, partition0);
        List<Integer> partition1Values = indexRepository.findEpochValuesByPartition(
                TEST_INDEX, TEST_ENTITY_ID, Granularity.DAY, partition1);

        assertThat(partition0Values).hasSize(2);
        assertThat(partition1Values).hasSize(1);
    }

    @Test
    @DisplayName("Delete by index name should remove all related data")
    void deleteByIndexName_shouldRemoveAllData() {
        indexService.mark(TEST_INDEX, TEST_ENTITY_ID, Granularity.DAY, 1704067200);
        indexService.mark(TEST_INDEX, TEST_ENTITY_ID + 1, Granularity.DAY, 1704067200);

        long countBefore = indexRepository.countByIndexName(TEST_INDEX);
        assertThat(countBefore).isGreaterThan(0);

        indexRepository.deleteByIndexName(TEST_INDEX);

        long countAfter = indexRepository.countByIndexName(TEST_INDEX);
        assertThat(countAfter).isEqualTo(0);
    }

    @Test
    @DisplayName("Count operations should return correct values")
    void countOperations_shouldReturnCorrectCounts() {
        indexService.mark(TEST_INDEX, TEST_ENTITY_ID, Granularity.DAY, 1704067200);
        indexService.mark(TEST_INDEX, TEST_ENTITY_ID + 1, Granularity.DAY, 1704067200);
        indexService.mark(TEST_INDEX, TEST_ENTITY_ID + 2, Granularity.DAY, 1704067200);

        long totalCount = indexRepository.countByIndexName(TEST_INDEX);
        long distinctEntities = indexRepository.countDistinctEntities(TEST_INDEX);

        assertThat(totalCount).isGreaterThanOrEqualTo(1);
        assertThat(distinctEntities).isEqualTo(3);
    }

    @Test
    @DisplayName("Find entity IDs should return all distinct entities")
    void findEntityIds_shouldReturnAllDistinctEntities() {
        Long[] entityIds = {100L, 200L, 300L};

        for (Long entityId : entityIds) {
            indexService.mark(TEST_INDEX, entityId, Granularity.DAY, 1704067200);
        }

        List<Long> foundEntityIds = indexRepository.findEntityIds(TEST_INDEX);

        assertThat(foundEntityIds).hasSize(3);
        assertThat(foundEntityIds).containsExactlyInAnyOrder(100L, 200L, 300L);
    }

    @Test
    @DisplayName("Duplicate writes should be handled gracefully")
    void save_duplicateData_shouldNotThrowException() {
        int epochValue = 1704067200;

        indexService.mark(TEST_INDEX, TEST_ENTITY_ID, Granularity.DAY, epochValue);
        indexService.mark(TEST_INDEX, TEST_ENTITY_ID, Granularity.DAY, epochValue);
        indexService.mark(TEST_INDEX, TEST_ENTITY_ID, Granularity.DAY, epochValue);

        long count = indexRepository.countByIndexName(TEST_INDEX);
        assertThat(count).isGreaterThanOrEqualTo(1);
    }
}
