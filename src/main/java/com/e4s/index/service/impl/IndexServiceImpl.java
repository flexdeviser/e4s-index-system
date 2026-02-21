package com.e4s.index.service.impl;

import com.e4s.index.model.Granularity;
import com.e4s.index.model.MeterIndex;
import com.e4s.index.model.TimeIndex;
import com.e4s.index.repository.IndexRepository;
import com.e4s.index.service.IndexService;
import com.e4s.index.service.IndexStats;
import com.e4s.index.util.PartitionUtils;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Implementation of {@link IndexService} using Redis as the backing store.
 * 
 * <p>This service provides:</p>
 * <ul>
 *   <li>LRU cache for frequently accessed data</li>
 *   <li>Redis persistence for durability</li>
 *   <li>Thread-safe operations</li>
 *   <li>Efficient bitmap serialization</li>
 * </ul>
 * 
 * <h2>Redis Key Structure</h2>
 * <ul>
 *   <li>{@code e4s:index:registry} - Set of all index names</li>
 *   <li>{@code e4s:index:{name}:{granularity}:{entityId}:{partition}} - Partitioned RoaringBitmap data</li>
 * </ul>
 * 
 * <h2>Thread Safety</h2>
 * <p>This implementation uses per-key read-write locks to ensure thread safety
 * while maintaining good concurrency for read-heavy workloads.</p>
 * 
 * @author E4S Team
 * @version 1.0.0
 * @see IndexService
 * @see TimeIndex
 */
public class IndexServiceImpl implements IndexService {

    private static final String INDEX_SET_KEY = "e4s:index:registry";

    private final RedisTemplate<String, byte[]> redisTemplate;
    private final int maxCacheSize;
    private final Object2ObjectMap<String, TimeIndex> cache;
    private final ConcurrentHashMap<String, ReadWriteLock> indexLocks;
    private final ConcurrentHashMap<String, Boolean> dirtyEntries;
    private final ConcurrentHashMap<String, TimeIndex> pendingPgWrites;
    private final ScheduledExecutorService flushExecutor;
    private final long flushIntervalMs;
    private final IndexRepository pgRepository;
    private final boolean persistenceEnabled;
    private final boolean asyncPgWrite;
    private volatile boolean closed;

    /**
     * Creates a new IndexServiceImpl with write-behind disabled (synchronous writes).
     * No PostgreSQL persistence.
     *
     * @param redisTemplate the Redis template for data access
     * @param maxCacheSize the maximum number of entries in the LRU cache
     */
    public IndexServiceImpl(RedisTemplate<String, byte[]> redisTemplate, int maxCacheSize) {
        this(redisTemplate, maxCacheSize, 0, null, false);
    }

    /**
     * Creates a new IndexServiceImpl with optional write-behind. No PostgreSQL persistence.
     *
     * @param redisTemplate the Redis template for data access
     * @param maxCacheSize the maximum number of entries in the LRU cache
     * @param flushIntervalMs flush interval in milliseconds. If 0, writes are synchronous.
     */
    public IndexServiceImpl(RedisTemplate<String, byte[]> redisTemplate, int maxCacheSize, long flushIntervalMs) {
        this(redisTemplate, maxCacheSize, flushIntervalMs, null, false);
    }

    /**
     * Creates a new IndexServiceImpl with PostgreSQL persistence.
     *
     * @param redisTemplate the Redis template for data access
     * @param maxCacheSize the maximum number of entries in the LRU cache
     * @param flushIntervalMs flush interval in milliseconds for Redis write-behind
     * @param pgRepository the PostgreSQL repository (can be null if persistence disabled)
     * @param asyncPgWrite whether to write to PostgreSQL asynchronously
     */
    public IndexServiceImpl(RedisTemplate<String, byte[]> redisTemplate, int maxCacheSize, 
                           long flushIntervalMs, IndexRepository pgRepository, boolean asyncPgWrite) {
        this.redisTemplate = redisTemplate;
        this.maxCacheSize = maxCacheSize;
        this.cache = new Object2ObjectLinkedOpenHashMap<>(maxCacheSize);
        this.cache.defaultReturnValue(null);
        this.indexLocks = new ConcurrentHashMap<>();
        this.dirtyEntries = new ConcurrentHashMap<>();
        this.pendingPgWrites = new ConcurrentHashMap<>();
        this.flushIntervalMs = flushIntervalMs;
        this.pgRepository = pgRepository;
        this.persistenceEnabled = pgRepository != null;
        this.asyncPgWrite = asyncPgWrite;
        this.closed = false;
        
        if (flushIntervalMs > 0) {
            this.flushExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "index-flush-executor");
                t.setDaemon(true);
                return t;
            });
            this.flushExecutor.scheduleAtFixedRate(this::flush, flushIntervalMs, flushIntervalMs, TimeUnit.MILLISECONDS);
        } else {
            this.flushExecutor = null;
        }
    }

    @Override
    public void createIndex(String indexName) {
        checkClosed();
        redisTemplate.opsForSet().add(INDEX_SET_KEY, indexName.getBytes());
    }

    @Override
    public boolean indexExists(String indexName) {
        checkClosed();
        boolean existsInRedis = Boolean.TRUE.equals(
                redisTemplate.opsForSet().isMember(INDEX_SET_KEY, indexName.getBytes()));
        if (existsInRedis) {
            return true;
        }
        
        if (persistenceEnabled && pgRepository != null) {
            return pgRepository.countByIndexName(indexName) > 0;
        }
        
        return false;
    }

    @Override
    public void deleteIndex(String indexName) {
        checkClosed();
        Set<String> keys = redisTemplate.keys(String.format("e4s:index:%s:*", indexName));
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
        redisTemplate.opsForSet().remove(INDEX_SET_KEY, indexName.getBytes());
        evictIndex(indexName);
        
        if (persistenceEnabled) {
            pgRepository.deleteByIndexName(indexName);
        }
    }

    @Override
    public List<String> listIndexes() {
        checkClosed();
        Set<byte[]> indexes = redisTemplate.opsForSet().members(INDEX_SET_KEY);
        List<String> result = new ArrayList<>();
        if (indexes != null) {
            for (byte[] index : indexes) {
                result.add(new String(index));
            }
        }
        return result;
    }

    @Override
    public void mark(String indexName, Long entityId, Granularity granularity, int value) {
        checkClosed();
        
        int partition = PartitionUtils.getPartition(value, granularity);
        String key = buildKey(indexName, granularity, entityId, value);
        ReadWriteLock lock = getLock(key);
        lock.writeLock().lock();
        try {
            TimeIndex index = getOrLoadForWrite(key);
            index.add(value);
            markDirty(key, index);
            
            if (persistenceEnabled) {
                saveToPostgres(indexName, entityId, granularity, value, partition, index);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    private void saveToPostgres(String indexName, Long entityId, Granularity granularity, 
                                int value, int partition, TimeIndex index) {
        savePartitionBitmapToPostgres(indexName, entityId, granularity, partition, index);
    }

    private void savePartitionBitmapToPostgres(String indexName, Long entityId, Granularity granularity, 
                                               int partition, TimeIndex index) {
        if (asyncPgWrite && flushIntervalMs > 0) {
            addToPgWriteBuffer(indexName, entityId, granularity, partition, index);
        } else if (asyncPgWrite) {
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                try {
                    savePartitionBitmapToPgSync(indexName, entityId, granularity, partition, index);
                } catch (Exception e) {
                    org.slf4j.LoggerFactory.getLogger(IndexServiceImpl.class)
                            .error("Failed to save partition bitmap to PostgreSQL: {}", e.getMessage());
                }
            });
        } else {
            try {
                savePartitionBitmapToPgSync(indexName, entityId, granularity, partition, index);
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(IndexServiceImpl.class)
                        .error("Failed to save partition bitmap to PostgreSQL: {}", e.getMessage());
            }
        }
    }

    private void addToPgWriteBuffer(String indexName, Long entityId, Granularity granularity, 
                                     int partition, TimeIndex index) {
        String pgKey = buildPgKey(indexName, entityId, granularity, partition);
        
        pendingPgWrites.compute(pgKey, (key, existingIndex) -> {
            if (existingIndex == null) {
                return index;
            }
            for (int v : index.toArray()) {
                existingIndex.add(v);
            }
            return existingIndex;
        });
    }

    private String buildPgKey(String indexName, Long entityId, Granularity granularity, int partition) {
        return indexName + ":" + entityId + ":" + granularity.name() + ":" + partition;
    }

    private void savePartitionBitmapToPgSync(String indexName, Long entityId, Granularity granularity, 
                                               int partition, TimeIndex index) {
        try {
            byte[] existingBitmap = pgRepository.getPartitionBitmap(indexName, entityId, granularity, partition);
            
            TimeIndex mergedIndex = new TimeIndex();
            
            if (existingBitmap != null && existingBitmap.length > 0) {
                TimeIndex pgIndex = TimeIndex.deserialize(existingBitmap);
                for (int v : pgIndex.toArray()) {
                    mergedIndex.add(v);
                }
            }
            
            int partitionSize = getPartitionSize(granularity);
            for (int v : index.toArray()) {
                int relativeValue = v % partitionSize;
                mergedIndex.add(relativeValue);
            }
            
            byte[] serialized = mergedIndex.serialize();
            pgRepository.savePartitionBitmap(indexName, entityId, granularity, partition, serialized);
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(IndexServiceImpl.class)
                    .error("Failed to save partition bitmap: {}", e.getMessage());
        }
    }

    private int getPartitionSize(Granularity granularity) {
        return switch (granularity) {
            case DAY -> 180;
            case MONTH -> 6;
            case YEAR -> 1;
        };
    }

    @Override
    public void markBatch(String indexName, Long entityId, Granularity granularity, int[] values) {
        checkClosed();
        if (values == null || values.length == 0) {
            return;
        }

        java.util.Map<Integer, java.util.List<Integer>> byPartition = new java.util.HashMap<>();
        for (int value : values) {
            int partition = PartitionUtils.getPartition(value, granularity);
            byPartition.computeIfAbsent(partition, k -> new java.util.ArrayList<>()).add(value);
        }

        for (var entry : byPartition.entrySet()) {
            int partition = entry.getKey();
            List<Integer> partitionValues = entry.getValue();
            String key = PartitionUtils.buildKey(indexName, granularity, entityId, partition);
            ReadWriteLock lock = getLock(key);
            lock.writeLock().lock();
            try {
                TimeIndex index = getOrLoadForWrite(key);
                for (int value : partitionValues) {
                    index.add(value);
                }
                markDirty(key, index);
                
                if (persistenceEnabled) {
                    savePartitionBitmapToPostgres(indexName, entityId, granularity, partition, index);
                }
            } finally {
                lock.writeLock().unlock();
            }
        }
    }
    
    private void saveBatchToPostgres(java.util.List<MeterIndex> indexes) {
        if (asyncPgWrite) {
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                try {
                    pgRepository.saveBatch(indexes);
                } catch (Exception e) {
                    org.slf4j.LoggerFactory.getLogger(IndexServiceImpl.class)
                            .error("Failed to batch save to PostgreSQL: {}", e.getMessage());
                }
            });
        } else {
            try {
                pgRepository.saveBatch(indexes);
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(IndexServiceImpl.class)
                        .error("Failed to batch save to PostgreSQL: {}", e.getMessage());
            }
        }
    }

    @Override
    public boolean exists(String indexName, Long entityId, Granularity granularity, int value) {
        checkClosed();
        String key = buildKey(indexName, granularity, entityId, value);
        TimeIndex index = getOrLoad(key);
        return index != null && index.contains(value);
    }

    @Override
    public Optional<Integer> findPrev(String indexName, Long entityId, Granularity granularity, int value) {
        checkClosed();
        return findPrevInternal(indexName, granularity, entityId, value, false);
    }

    private Optional<Integer> findPrevInternal(String indexName, Granularity granularity, Long entityId, int value, boolean fromPrevPartition) {
        String key = buildKey(indexName, granularity, entityId, value);
        TimeIndex index = getOrLoad(key);
        
        if (index != null) {
            Optional<Integer> result = index.findPrev(value);
            if (result.isPresent()) {
                return result;
            }
        }
        
        if (!fromPrevPartition) {
            String prevKey = PartitionUtils.getPrevPartitionKey(indexName, granularity, entityId, value);
            if (prevKey != null) {
                TimeIndex prevIndex = loadFromRedis(prevKey);
                if (prevIndex != null) {
                    return prevIndex.findPrev(Integer.MAX_VALUE);
                }
            }
        }
        
        return Optional.empty();
    }

    @Override
    public Optional<Integer> findNext(String indexName, Long entityId, Granularity granularity, int value) {
        checkClosed();
        return findNextInternal(indexName, granularity, entityId, value, false);
    }

    private Optional<Integer> findNextInternal(String indexName, Granularity granularity, Long entityId, int value, boolean fromNextPartition) {
        String key = buildKey(indexName, granularity, entityId, value);
        TimeIndex index = getOrLoad(key);
        
        if (index != null) {
            Optional<Integer> result = index.findNext(value);
            if (result.isPresent()) {
                return result;
            }
        }
        
        if (!fromNextPartition) {
            String nextKey = PartitionUtils.getNextPartitionKey(indexName, granularity, entityId, value);
            TimeIndex nextIndex = loadFromRedis(nextKey);
            if (nextIndex != null) {
                return nextIndex.findNext(Integer.MIN_VALUE);
            }
        }
        
        return Optional.empty();
    }

    private TimeIndex loadFromRedis(String key) {
        if (key == null) {
            return null;
        }
        try {
            byte[] data = redisTemplate.opsForValue().get(key);
            if (data != null && data.length > 0) {
                return TimeIndex.deserialize(data);
            }
        } catch (Exception e) {
            // Log and return null
        }
        return null;
    }

    @Override
    public void evictEntity(String indexName, Long entityId) {
        for (Granularity g : Granularity.values()) {
            for (int partition = 0; partition < 20; partition++) {
                String key = PartitionUtils.buildKey(indexName, g, entityId, partition);
                cache.remove(key);
            }
        }
    }

    @Override
    public void evictIndex(String indexName) {
        Set<String> keys = redisTemplate.keys(String.format("e4s:index:%s:*", indexName));
        if (keys != null) {
            for (String key : keys) {
                cache.remove(key);
            }
        }
    }

    @Override
    public long entityCount(String indexName) {
        Set<String> keys = redisTemplate.keys(String.format("e4s:index:%s:DAY:*", indexName));
        return keys != null ? keys.size() : 0L;
    }

    @Override
    public IndexStats getStats(String indexName) {
        return new IndexStats(
                entityCount(indexName),
                cache.size(),
                calculateMemoryUsage()
        );
    }

    /**
     * Closes this service and releases resources.
     */
    public void close() {
        closed = true;
        if (flushExecutor != null) {
            flush();
            flushExecutor.shutdown();
            try {
                if (!flushExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                    flushExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                flushExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        cache.clear();
        indexLocks.clear();
        dirtyEntries.clear();
    }

    private String buildKey(String indexName, Granularity granularity, Long entityId, int value) {
        return PartitionUtils.buildKeyForValue(indexName, granularity, entityId, value);
    }

    private ReadWriteLock getLock(String key) {
        return indexLocks.computeIfAbsent(key, k -> new ReentrantReadWriteLock());
    }

    private TimeIndex getOrLoad(String key) {
        TimeIndex index = cache.get(key);
        if (index != null) {
            return index;
        }

        ReadWriteLock lock = getLock(key);
        lock.writeLock().lock();
        try {
            index = cache.get(key);
            if (index != null) {
                return index;
            }
            byte[] data = redisTemplate.opsForValue().get(key);
            if (data != null && data.length > 0) {
                try {
                    index = TimeIndex.deserialize(data);
                    putInCache(key, index);
                    return index;
                } catch (Exception e) {
                    return null;
                }
            }
            
            if (persistenceEnabled && pgRepository != null) {
                index = loadFromPostgres(key);
                if (index != null) {
                    putInCache(key, index);
                    return index;
                }
            }
            
            return null;
        } finally {
            lock.writeLock().unlock();
        }
    }

    private TimeIndex getOrLoadForWrite(String key) {
        TimeIndex index = cache.get(key);
        if (index != null) {
            return index;
        }
        
        byte[] data = redisTemplate.opsForValue().get(key);
        if (data != null && data.length > 0) {
            try {
                index = TimeIndex.deserialize(data);
            } catch (Exception e) {
                index = new TimeIndex();
            }
        } else if (persistenceEnabled && pgRepository != null) {
            index = loadFromPostgres(key);
            if (index == null) {
                index = new TimeIndex();
            }
        } else {
            index = new TimeIndex();
        }
        
        putInCache(key, index);
        return index;
    }

    private TimeIndex loadFromPostgres(String key) {
        try {
            String[] parts = key.split(":");
            if (parts.length != 5) {
                return null;
            }
            String indexName = parts[2];
            Granularity granularity = Granularity.valueOf(parts[3].toUpperCase());
            long entityId = Long.parseLong(parts[4]);
            int partition = parsePartition(key, granularity);
            
            byte[] bitmapData = pgRepository.getPartitionBitmap(indexName, entityId, granularity, partition);
            if (bitmapData != null && bitmapData.length > 0) {
                redisTemplate.opsForSet().add(INDEX_SET_KEY, indexName.getBytes());
                return TimeIndex.deserialize(bitmapData);
            }
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(IndexServiceImpl.class)
                    .debug("Failed to load from PostgreSQL: {}", e.getMessage());
        }
        return null;
    }

    private int parsePartition(String key, Granularity granularity) {
        String[] parts = key.split(":");
        if (parts.length >= 5) {
            return Integer.parseInt(parts[4]);
        }
        return 0;
    }

    private void markDirty(String key, TimeIndex index) {
        if (flushIntervalMs > 0) {
            dirtyEntries.put(key, Boolean.TRUE);
        } else {
            save(key, index);
        }
    }

    /**
     * Flushes all dirty entries to Redis. Called periodically by the scheduled executor
     * or can be called manually to force an immediate flush.
     */
    public void flush() {
        if (dirtyEntries.isEmpty() && pendingPgWrites.isEmpty()) {
            return;
        }
        
        for (String key : dirtyEntries.keySet()) {
            TimeIndex index = cache.get(key);
            if (index != null) {
                save(key, index);
            }
        }
        dirtyEntries.clear();
        
        flushPendingPgWrites();
    }

    private void flushPendingPgWrites() {
        if (pendingPgWrites.isEmpty()) {
            return;
        }
        
        for (var entry : pendingPgWrites.entrySet()) {
            String pgKey = entry.getKey();
            TimeIndex index = entry.getValue();
            
            String[] parts = pgKey.split(":");
            if (parts.length != 4) continue;
            
            String indexName = parts[0];
            Long entityId = Long.parseLong(parts[1]);
            Granularity granularity = Granularity.valueOf(parts[2]);
            int partition = Integer.parseInt(parts[3]);
            
            try {
                savePartitionBitmapToPgSync(indexName, entityId, granularity, partition, index);
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(IndexServiceImpl.class)
                        .error("Failed to flush partition bitmap to PostgreSQL: {}", e.getMessage());
            }
        }
        pendingPgWrites.clear();
    }

    /**
     * Flushes a specific key to Redis immediately.
     */
    public void flushKey(String key) {
        TimeIndex index = cache.get(key);
        if (index != null) {
            save(key, index);
            dirtyEntries.remove(key);
        }
    }

    private void save(String key, TimeIndex index) {
        try {
            byte[] data = index.serialize();
            redisTemplate.opsForValue().set(key, data);
        } catch (Exception e) {
            throw new RuntimeException("Failed to save index: " + key, e);
        }
    }

    private void putInCache(String key, TimeIndex index) {
        if (cache.size() >= maxCacheSize) {
            String firstKey = cache.keySet().iterator().next();
            TimeIndex evictedIndex = cache.get(firstKey);
            if (evictedIndex != null && dirtyEntries.containsKey(firstKey)) {
                save(firstKey, evictedIndex);
                dirtyEntries.remove(firstKey);
            }
            cache.remove(firstKey);
        }
        cache.put(key, index);
    }

    private long calculateMemoryUsage() {
        long total = 0;
        for (TimeIndex index : cache.values()) {
            total += index.sizeInBytes();
        }
        return total;
    }

    private void checkClosed() {
        if (closed) {
            throw new IllegalStateException("Index service is closed");
        }
    }

    public IndexRepository getPgRepository() {
        return pgRepository;
    }

    public boolean isPersistenceEnabled() {
        return persistenceEnabled;
    }
}
