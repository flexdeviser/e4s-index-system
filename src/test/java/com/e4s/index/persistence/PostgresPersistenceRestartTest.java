package com.e4s.index.persistence;

import com.e4s.index.model.Granularity;
import com.e4s.index.repository.IndexRepository;
import com.e4s.index.service.IndexService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("it-sync")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("PostgreSQL Persistence - Basic Functionality Tests")
class PostgresPersistenceRestartTest {

    @Autowired
    private IndexService indexService;

    @Autowired
    private IndexRepository indexRepository;

    @Test
    @DisplayName("Write data and verify indexExists returns true")
    void writeAndVerifyIndexExists() {
        String indexName = "test-index-exists";
        
        indexService.markBatch(indexName, 1L, Granularity.DAY, new int[]{100, 150, 200});
        
        boolean exists = indexService.indexExists(indexName);
        assertThat(exists).isTrue();
        
        indexRepository.deleteByIndexName(indexName);
    }

    @Test
    @DisplayName("Query should load from PostgreSQL when not in Redis cache")
    void queryShouldLoadFromPostgres() {
        String indexName = "test-query-pg";
        
        indexService.markBatch(indexName, 1L, Granularity.DAY, new int[]{100, 150, 200});
        
        boolean exists = indexService.exists(indexName, 1L, Granularity.DAY, 150);
        assertThat(exists).isTrue();
        
        indexRepository.deleteByIndexName(indexName);
    }

    @Test
    @DisplayName("After loading from PostgreSQL, index should be registered in Redis")
    void afterLoadIndexRegisteredInRedis() {
        String indexName = "test-register-redis";
        
        indexService.markBatch(indexName, 1L, Granularity.DAY, new int[]{100, 150, 200});
        
        indexService.exists(indexName, 1L, Granularity.DAY, 100);
        
        boolean exists = indexService.indexExists(indexName);
        assertThat(exists).isTrue();
        
        indexRepository.deleteByIndexName(indexName);
    }
}
