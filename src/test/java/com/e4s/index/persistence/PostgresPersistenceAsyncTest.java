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

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("it-async")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@DisplayName("PostgreSQL Persistence - Asynchronous Write Tests")
class PostgresPersistenceAsyncTest {

    @Autowired
    private IndexService indexService;

    @Autowired
    private IndexRepository indexRepository;

    @Autowired
    private IndexProperties indexProperties;

    private static final String TEST_INDEX = "async-test-index";
    private static final Long TEST_ENTITY_ID = 2001L;

    @BeforeEach
    void setUp() {
        assertThat(indexProperties.getPersistence().isEnabled()).isTrue();
        assertThat(indexProperties.getPersistence().isAsyncWrite()).isTrue();
        assertThat(indexRepository).isNotNull();
        indexRepository.deleteByIndexName(TEST_INDEX);
    }

    @AfterEach
    void cleanup() {
        indexRepository.deleteByIndexName(TEST_INDEX);
    }

    @Test
    @DisplayName("mark() should eventually persist data asynchronously to PostgreSQL")
    void mark_asyncWrite_shouldPersistEventually() throws Exception {
        int epochValue = 1704067200;

        indexService.mark(TEST_INDEX, TEST_ENTITY_ID, Granularity.DAY, epochValue);

        Thread.sleep(500);

        boolean exists = indexRepository.exists(TEST_INDEX, TEST_ENTITY_ID, Granularity.DAY, epochValue);
        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("markBatch() should eventually persist data asynchronously to PostgreSQL")
    void markBatch_asyncWrite_shouldPersistEventually() throws Exception {
        int[] epochValues = {1704067200, 1704153600, 1704240000};

        indexService.markBatch(TEST_INDEX, TEST_ENTITY_ID, Granularity.DAY, epochValues);

        Thread.sleep(500);

        for (int epochValue : epochValues) {
            boolean exists = indexRepository.exists(TEST_INDEX, TEST_ENTITY_ID, Granularity.DAY, epochValue);
            assertThat(exists).isTrue();
        }
    }

    @Test
    @DisplayName("Multiple async writes should all be persisted")
    void mark_multipleAsyncWrites_shouldAllBePersisted() throws Exception {
        int[] epochValues = {1704067200, 1704153600, 1704240000, 1704326400, 1704412800};

        for (int epochValue : epochValues) {
            indexService.mark(TEST_INDEX, TEST_ENTITY_ID, Granularity.DAY, epochValue);
        }

        Thread.sleep(1000);

        List<Integer> foundValues = indexRepository.findEpochValues(TEST_INDEX, TEST_ENTITY_ID, Granularity.DAY);

        assertThat(foundValues).hasSize(5);
    }

    @Test
    @DisplayName("Async write should work with different granularities")
    void mark_differentGranularities_shouldPersistForAll() throws Exception {
        int epochDay = 1704067200;
        int epochMonth = 1704067200 / 86400;
        int epochYear = 1704067200 / 86400 / 30;

        indexService.mark(TEST_INDEX, TEST_ENTITY_ID, Granularity.DAY, epochDay);
        indexService.mark(TEST_INDEX, TEST_ENTITY_ID, Granularity.MONTH, epochMonth);
        indexService.mark(TEST_INDEX, TEST_ENTITY_ID, Granularity.YEAR, epochYear);

        Thread.sleep(500);

        assertThat(indexRepository.exists(TEST_INDEX, TEST_ENTITY_ID, Granularity.DAY, epochDay)).isTrue();
        assertThat(indexRepository.exists(TEST_INDEX, TEST_ENTITY_ID, Granularity.MONTH, epochMonth)).isTrue();
        assertThat(indexRepository.exists(TEST_INDEX, TEST_ENTITY_ID, Granularity.YEAR, epochYear)).isTrue();
    }

    @Test
    @DisplayName("Async write should work with multiple entities")
    void mark_multipleEntities_shouldPersistForAll() throws Exception {
        Long[] entityIds = {3001L, 3002L, 3003L};
        int epochValue = 1704067200;

        for (Long entityId : entityIds) {
            indexService.mark(TEST_INDEX, entityId, Granularity.DAY, epochValue);
        }

        Thread.sleep(500);

        for (Long entityId : entityIds) {
            boolean exists = indexRepository.exists(TEST_INDEX, entityId, Granularity.DAY, epochValue);
            assertThat(exists).isTrue();
        }
    }

    @Test
    @DisplayName("Data should be queryable after async persistence")
    void findEpochValues_afterAsyncWrite_shouldReturnAllData() throws Exception {
        int[] epochValues = {1704067200, 1704153600, 1704240000};

        indexService.markBatch(TEST_INDEX, TEST_ENTITY_ID, Granularity.DAY, epochValues);

        Thread.sleep(1000);

        List<Integer> foundValues = indexRepository.findEpochValues(TEST_INDEX, TEST_ENTITY_ID, Granularity.DAY);

        assertThat(foundValues).hasSize(3);
        assertThat(foundValues).containsExactlyInAnyOrder(1704067200, 1704153600, 1704240000);
    }

    @Test
    @DisplayName("Delete index should work after async writes")
    void deleteIndex_afterAsyncWrites_shouldDeleteAll() throws Exception {
        int epochValue = 1704067200;

        indexService.mark(TEST_INDEX, TEST_ENTITY_ID, Granularity.DAY, epochValue);
        indexService.mark(TEST_INDEX, TEST_ENTITY_ID + 1, Granularity.DAY, epochValue);

        Thread.sleep(500);

        indexService.deleteIndex(TEST_INDEX);

        Thread.sleep(500);

        long count = indexRepository.countByIndexName(TEST_INDEX);
        assertThat(count).isEqualTo(0);
    }
}
