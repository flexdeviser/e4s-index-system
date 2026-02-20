package com.e4s.index.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for the E4S Index System.
 * 
 * <p>These properties can be configured in application.yml:</p>
 * <pre>
 * index:
 *   cache:
 *     max-size: 100000
 * </pre>
 * 
 * @author E4S Team
 * @version 1.0.0
 */
@Configuration
@ConfigurationProperties(prefix = "index")
public class IndexProperties {

    private Cache cache = new Cache();

    /**
     * Gets the cache configuration.
     *
     * @return the cache configuration
     */
    public Cache getCache() {
        return cache;
    }

    /**
     * Sets the cache configuration.
     *
     * @param cache the cache configuration
     */
    public void setCache(Cache cache) {
        this.cache = cache;
    }

    /**
     * Cache configuration properties.
     */
    public static class Cache {
        private int maxSize = 100000;

        /**
         * Gets the maximum cache size.
         *
         * @return the maximum number of entries in the cache
         */
        public int getMaxSize() {
            return maxSize;
        }

        /**
         * Sets the maximum cache size.
         *
         * @param maxSize the maximum number of entries in the cache
         */
        public void setMaxSize(int maxSize) {
            this.maxSize = maxSize;
        }
    }
}
