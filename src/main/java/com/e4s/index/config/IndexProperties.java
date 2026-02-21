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
 *   persistence:
 *     enabled: true
 *     schema: e4s_index
 *     batch-size: 1000
 *     async-write: true
 * </pre>
 * 
 * @author E4S Team
 * @version 1.0.0
 */
@Configuration
@ConfigurationProperties(prefix = "index")
public class IndexProperties {

    private Cache cache = new Cache();
    private Persistence persistence = new Persistence();

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
     * Gets the persistence configuration.
     *
     * @return the persistence configuration
     */
    public Persistence getPersistence() {
        return persistence;
    }

    /**
     * Sets the persistence configuration.
     *
     * @param persistence the persistence configuration
     */
    public void setPersistence(Persistence persistence) {
        this.persistence = persistence;
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

    /**
     * Persistence configuration properties for PostgreSQL.
     */
    public static class Persistence {
        private boolean enabled = false;
        private String schema = "e4s_index";
        private int batchSize = 1000;
        private boolean asyncWrite = true;
        private long flushIntervalMs = 100;
        private int asyncQueueSize = 10000;
        private int asyncCoreThreads = 4;
        private int asyncMaxThreads = 8;

        /**
         * Gets whether persistence is enabled.
         *
         * @return true if enabled
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * Sets whether persistence is enabled.
         *
         * @param enabled true to enable
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /**
         * Gets the PostgreSQL schema name.
         *
         * @return the schema name
         */
        public String getSchema() {
            return schema;
        }

        /**
         * Sets the PostgreSQL schema name.
         *
         * @param schema the schema name
         */
        public void setSchema(String schema) {
            this.schema = schema;
        }

        /**
         * Gets the batch size for bulk operations.
         *
         * @return the batch size
         */
        public int getBatchSize() {
            return batchSize;
        }

        /**
         * Sets the batch size for bulk operations.
         *
         * @param batchSize the batch size
         */
        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }

        /**
         * Gets whether writes to PostgreSQL should be async.
         *
         * @return true if async
         */
        public boolean isAsyncWrite() {
            return asyncWrite;
        }

        /**
         * Sets whether writes to PostgreSQL should be async.
         *
         * @param asyncWrite true for async
         */
        public void setAsyncWrite(boolean asyncWrite) {
            this.asyncWrite = asyncWrite;
        }

        /**
         * Gets the flush interval for Redis write-behind.
         *
         * @return flush interval in milliseconds
         */
        public long getFlushIntervalMs() {
            return flushIntervalMs;
        }

        /**
         * Sets the flush interval for Redis write-behind.
         *
         * @param flushIntervalMs flush interval in milliseconds
         */
        public void setFlushIntervalMs(long flushIntervalMs) {
            this.flushIntervalMs = flushIntervalMs;
        }

        public int getAsyncQueueSize() {
            return asyncQueueSize;
        }

        public void setAsyncQueueSize(int asyncQueueSize) {
            this.asyncQueueSize = asyncQueueSize;
        }

        public int getAsyncCoreThreads() {
            return asyncCoreThreads;
        }

        public void setAsyncCoreThreads(int asyncCoreThreads) {
            this.asyncCoreThreads = asyncCoreThreads;
        }

        public int getAsyncMaxThreads() {
            return asyncMaxThreads;
        }

        public void setAsyncMaxThreads(int asyncMaxThreads) {
            this.asyncMaxThreads = asyncMaxThreads;
        }
    }
}
