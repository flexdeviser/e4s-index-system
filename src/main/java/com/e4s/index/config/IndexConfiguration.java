package com.e4s.index.config;

import com.e4s.index.repository.IndexRepository;
import com.e4s.index.repository.PostgresIndexRepository;
import com.e4s.index.service.IndexService;
import com.e4s.index.service.impl.IndexServiceImpl;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * Configuration class for the E4S Index System.
 * 
 * <p>This class configures:</p>
 * <ul>
 *   <li>Redis template for byte array storage</li>
 *   <li>Index service with LRU cache</li>
 *   <li>PostgreSQL repository (when persistence enabled)</li>
 * </ul>
 * 
 * @author E4S Team
 * @version 1.0.0
 */
@Configuration
public class IndexConfiguration {

    /**
     * Creates a RedisTemplate configured for byte array storage.
     * 
     * <p>This template uses:</p>
     * <ul>
     *   <li>String serializer for keys</li>
     *   <li>Byte array serializer for values</li>
     * </ul>
     *
     * @param connectionFactory the Redis connection factory
     * @return configured RedisTemplate
     */
    @Bean
    public RedisTemplate<String, byte[]> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, byte[]> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(RedisSerializer.byteArray());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(RedisSerializer.byteArray());
        template.afterPropertiesSet();
        return template;
    }

    /**
     * Creates a JdbcTemplate for PostgreSQL access.
     * Only created when persistence is enabled.
     *
     * @param dataSource the data source
     * @return configured JdbcTemplate
     */
    @Bean
    @ConditionalOnProperty(name = "index.persistence.enabled", havingValue = "true", matchIfMissing = false)
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    /**
     * Creates the IndexRepository.
     *
     * @param jdbcTemplate the JDBC template
     * @param properties the index configuration properties
     * @return the repository
     */
    @Bean
    @ConditionalOnProperty(name = "index.persistence.enabled", havingValue = "true", matchIfMissing = false)
    public IndexRepository indexRepository(JdbcTemplate jdbcTemplate, 
                                                     IndexProperties properties) {
        return new PostgresIndexRepository(jdbcTemplate, properties);
    }

    /**
     * Creates the IndexService with the configured cache size.
     *
     * @param redisTemplate the Redis template
     * @param properties the index configuration properties
     * @param repository the index repository (can be null if persistence disabled)
     * @return configured IndexService
     */
    @Bean
    public IndexService indexService(RedisTemplate<String, byte[]> redisTemplate, 
                                     IndexProperties properties,
                                     java.util.Optional<IndexRepository> repository) {
        if (properties.getPersistence().isEnabled() && repository.isPresent()) {
            return new IndexServiceImpl(
                    redisTemplate, 
                    properties.getCache().getMaxSize(),
                    properties.getPersistence().getFlushIntervalMs(),
                    repository.get(),
                    properties.getPersistence().isAsyncWrite()
            );
        } else {
            return new IndexServiceImpl(
                    redisTemplate, 
                    properties.getCache().getMaxSize(),
                    properties.getPersistence().getFlushIntervalMs()
            );
        }
    }
}
