# E4S Index System - Design Document

## Overview

E4S Index System is a multi-tenant time-series data index service that enables fast lookups of data existence across different time granularities (DAY, MONTH, YEAR) without querying the primary database.

## Problem Statement

When processing time-series data for millions of meters, checking if data exists for a specific time period requires expensive database queries. This is particularly problematic during:
- System restarts and data reloads
- Gap detection in processed data
- Finding previous/next available data points

## Solution

A lightweight index service that tracks which time periods have data for each entity, stored in Redis with an in-memory LRU cache for fast access.

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                     Client Applications                          │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                    REST API (Spring Boot)                        │
│  POST /api/v1/index          - Create index                     │
│  GET  /api/v1/index          - List indexes                     │
│  GET  /api/v1/index/{name}   - Get index info                   │
│  DELETE /api/v1/index/{name} - Delete index                     │
│  POST /api/v1/index/exists   - Check existence                  │
│  POST /api/v1/index/prev     - Find previous value              │
│  POST /api/v1/index/next     - Find next value                  │
│  POST /api/v1/index/mark     - Mark timestamps                  │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Index Service Layer                           │
│                                                                  │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │  LRU Cache (configurable size, default 100,000)          │   │
│  │  - Hot data kept in memory                               │   │
│  │  - O(1) lookup for cached entries                        │   │
│  │  - Evicts least recently used when full                  │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                  │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │  TimeIndex (per entity per granularity)                  │   │
│  │  - RoaringBitmap for compact storage                     │   │
│  │  - O(1) existence check                                  │   │
│  │  - O(log n) prev/next navigation                         │   │
│  └─────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                       Redis                                      │
│                                                                  │
│  Key Structure:                                                  │
│    e4s:index:registry                    - Set of index names   │
│    e4s:index:{name}:{granularity}:{id}   - RoaringBitmap data  │
│                                                                  │
│  Example:                                                        │
│    e4s:index:meter-data:day:12345        - Days with data      │
│    e4s:index:meter-data:month:12345      - Months with data    │
│    e4s:index:meter-data:year:12345       - Years with data     │
└─────────────────────────────────────────────────────────────────┘
```

---

## Data Model

### Granularity

| Level | Description | Internal Value | Example Input | Example Output |
|-------|-------------|----------------|---------------|----------------|
| DAY | Day-level tracking | Days since Unix epoch | 1704067200000 (Jan 1, 2024) | 19723 |
| MONTH | Month-level tracking | Months since Jan 1970 | 1704067200000 (Jan 2024) | 648 |
| YEAR | Year-level tracking | Years since 1970 | 1704067200000 (2024) | 54 |

### TimeIndex

Each entity has a `TimeIndex` per granularity, implemented as a `RoaringBitmap`:
- **Compact storage**: ~2 bytes per entry for dense data
- **Fast operations**: O(1) contains, O(log n) prev/next
- **Serialization**: Efficient byte array for Redis storage

---

## API Specification

### Create Index

```http
POST /api/v1/index
Content-Type: application/json

{
  "indexName": "meter-data"
}
```

### Mark Timestamps

```http
POST /api/v1/index/mark
Content-Type: application/json

{
  "indexName": "meter-data",
  "entityId": 12345,
  "granularity": "DAY",
  "timestamps": [1704067200000, 1704153600000]
}
```

### Check Existence

```http
POST /api/v1/index/exists
Content-Type: application/json

{
  "indexName": "meter-data",
  "entityId": 12345,
  "granularity": "DAY",
  "timestamp": 1704067200000
}

Response:
{
  "indexName": "meter-data",
  "entityId": 12345,
  "granularity": "DAY",
  "timestamp": 1704067200000,
  "exists": true
}
```

### Find Previous/Next

```http
POST /api/v1/index/prev
Content-Type: application/json

{
  "indexName": "meter-data",
  "entityId": 12345,
  "granularity": "DAY",
  "timestamp": 1704153600000
}

Response:
{
  "indexName": "meter-data",
  "entityId": 12345,
  "granularity": "DAY",
  "timestamp": 1704153600000,
  "result": 1704067200000
}
```

---

## Configuration

```yaml
# application.yml
spring:
  data:
    redis:
      host: localhost
      port: 6379
      database: 0
      timeout: 5000ms

index:
  cache:
    max-size: 100000  # Maximum entities in LRU cache
```

### Write-Behind (Async Writes)

For high-throughput write workloads, enable write-behind mode:

```java
// Synchronous writes (default, 0 = disabled)
IndexService service = new IndexServiceImpl(redisTemplate, 100000, 0);

// Write-behind with 100ms flush interval
IndexService service = new IndexServiceImpl(redisTemplate, 100000, 100);
```

| Flush Interval | Use Case | Data at Risk (max) |
|----------------|----------|-------------------|
| 0 | Durability critical | None |
| 10ms | Low latency | ~10ms writes |
| 100ms | Balanced (default) | ~100ms writes |
| 1000ms | High throughput | ~1s writes |

**Risk Mitigation:**
- Dirty entries are flushed before cache eviction
- `close()` method flushes all pending writes before shutdown
- For critical data, enable Redis AOF: `redis-cli config set appendonly yes`

**Trade-off:** Write-behind provides ~100-300x write throughput but introduces eventual consistency.

---

## Partitioned Index

For memory efficiency, indexes are partitioned by time intervals instead of holding a single bitmap per entity.

### Partition Size

| Granularity | Partition Interval | Bits per Partition |
|-------------|-------------------|-------------------|
| DAY | 180 days | ~180 bits |
| MONTH | 6 months | ~6 bits |
| YEAR | 1 year | 1 bit |

### Key Structure

```
e4s:index:{name}:{granularity}:{entityId}:{partition}
Example: e4s:index:meter-data:day:12345:109
```

Where partition = epochValue / 180 (for DAY)

### Memory Comparison

| Scenario | Non-Partitioned | Partitioned (180-day) |
|----------|-----------------|----------------------|
| Per meter (DAY) | 744 bytes | ~50 bytes |
| Cache 1M meters | 744 MB | ~50 MB |
| Memory reduction | - | **~15x** |

### Cross-Partition Queries

For `findPrev` and `findNext` queries near partition boundaries, the service may need to check adjacent partitions.

**Example: `findPrev(June 15, 2025)`**

```
Partition 111: Jan 1 - May 29, 2025 (days 19723-20159)
Partition 112: May 30 - Nov 25, 2025 (days 20160-20639)
```

1. Check partition 112 for value < day 20178
2. If not found, load partition 111 from Redis and find max value

**Performance Impact:**

| Scenario | Redis Calls | Notes |
|----------|-------------|-------|
| Value in same partition | 0-1 | Cache hit or single load |
| Value in adjacent partition | 1-2 | Extra Redis call |

This is why `findPrev`/`findNext` are slower (~550K ops/sec) compared to `exists` (~1.9M ops/sec) in benchmarks.

### Migration

Partitioned index is the new default format. There is no automatic migration from non-partitioned keys - fresh deployment recommended.

---

## Memory Estimation

### Per Entity

| Granularity | Storage |
|-------------|---------|
| DAY (1 year, dense) | ~744 bytes |
| MONTH (1 year) | ~38 bytes |
| YEAR (1 year) | ~10 bytes |

### Scale Example

| Scale | Day Index (1 year) | Memory Budget |
|-------|-------------------|---------------|
| 100 meters | 74 KB | 2 GB |
| 10,000 meters | 7.4 MB | 2 GB |
| 1,000,000 meters | 744 MB | 2 GB |
| 10,000,000 meters | 7.4 GB | Requires scaling |

---

## Deployment

### Docker Compose

```yaml
services:
  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
    command: redis-server --maxmemory 256mb

  index-service:
    build: .
    ports:
      - "8080:8080"
    environment:
      SPRING_DATA_REDIS_HOST: redis
      INDEX_CACHE_MAX_SIZE: 100000
    depends_on:
      - redis
```

### Docker Commands

```bash
# Build and run
docker-compose up -d

# Check logs
docker-compose logs -f index-service

# Stop
docker-compose down
```

---

## Dependencies

| Library | Purpose |
|---------|---------|
| Spring Boot 3.2 | Web framework, DI |
| Spring Data Redis | Redis integration |
| RoaringBitmap 1.0.5 | Compact bitmap storage |
| fastutil 8.5.12 | Primitive collections |

---

## Project Structure

```
src/main/java/com/e4s/index/
├── IndexApplication.java          # Spring Boot entry point
├── config/
│   ├── IndexConfiguration.java    # Bean configuration
│   └── IndexProperties.java       # Configuration properties
├── controller/
│   ├── IndexController.java       # REST endpoints
│   └── GlobalExceptionHandler.java
├── dto/
│   ├── CreateIndexRequest.java
│   ├── ExistsResponse.java
│   ├── IndexInfo.java
│   ├── MarkRequest.java
│   ├── NavigationResponse.java
│   ├── QueryRequest.java
│   └── ErrorResponse.java
├── model/
│   ├── Granularity.java           # Enum: DAY, MONTH, YEAR
│   └── TimeIndex.java             # RoaringBitmap wrapper
├── service/
│   ├── IndexService.java          # Interface
│   ├── IndexStats.java            # Stats record
│   └── impl/IndexServiceImpl.java # Implementation
└── util/
    └── TimeUtils.java             # Time conversion utilities
```

---

## Performance Characteristics

### Time Complexity

| Operation | Complexity |
|-----------|------------|
| exists | O(1) |
| findPrev | O(log n) |
| findNext | O(log n) |
| mark | O(1) per value |

### Space Complexity

- Dense data: ~2 bytes per entry
- Sparse data: ~4-8 bytes per entry

---

## Future Enhancements

1. **Persistence options**: Support for PostgreSQL alongside Redis
2. **TTL support**: Auto-expiration of old index data
3. **Batch operations**: Bulk existence checks
4. **Metrics**: Prometheus integration for monitoring
5. **Horizontal scaling**: Support for Redis Cluster
