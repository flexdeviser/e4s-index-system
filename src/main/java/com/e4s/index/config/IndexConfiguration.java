package com.e4s.index.config;

import com.e4s.index.service.IndexService;
import com.e4s.index.service.impl.IndexServiceImpl;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Configuration class for the E4S Index System.
 * 
 * <p>This class configures:</p>
 * <ul>
 *   <li>Redis template for byte array storage</li>
 *   <li>Index service with LRU cache</li>
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
     * Creates the IndexService with the configured cache size.
     *
     * @param redisTemplate the Redis template
     * @param properties the index configuration properties
     * @return configured IndexService
     */
    @Bean
    public IndexService indexService(RedisTemplate<String, byte[]> redisTemplate, 
                                     IndexProperties properties) {
        return new IndexServiceImpl(redisTemplate, properties.getCache().getMaxSize());
    }
}
