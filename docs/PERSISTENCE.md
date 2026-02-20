# PostgreSQL Persistence Guide

## Overview

The E4S Index System can persist index data to PostgreSQL as the source of truth. This ensures that even if Redis data is lost, the index can be rebuilt from PostgreSQL.

## Architecture

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│  Client App     │────▶│  E4S Index API  │────▶│  Redis          │
│                 │     │                 │     │  (hot cache)   │
└─────────────────┘     └────────┬────────┘     └─────────────────┘
                                 │
                                 ▼
                        ┌─────────────────┐
                        │  PostgreSQL     │
                        │  (source truth)│
                        └─────────────────┘
```

## Quick Start

### 1. Enable PostgreSQL in docker-compose

```yaml
# docker-compose.yml
services:
  postgres:
    image: postgres:15-alpine
    environment:
      POSTGRES_DB: meterdb
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
    volumes:
      - ./src/main/resources/schema-postgres.sql:/docker-entrypoint-initdb.d/01-schema.sql

  index-service:
    environment:
      INDEX_PERSISTENCE_ENABLED: "true"
      INDEX_PERSISTENCE_ASYNC_WRITE: "true"
```

### 2. Configure application.yml

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/meterdb
    username: postgres
    password: postgres
    
index:
  persistence:
    enabled: true
    schema: e4s_index
    batch-size: 1000
    async-write: true
```

## Configuration Parameters

### index.persistence.enabled

| Value | Description |
|-------|-------------|
| `false` (default) | PostgreSQL persistence disabled |
| `true` | PostgreSQL persistence enabled |

When disabled, the system operates in Redis-only mode (no source of truth).

**Example:**
```yaml
index:
  persistence:
    enabled: true
```

---

### index.persistence.schema

| Type | Default | Description |
|------|---------|-------------|
| String | `e4s_index` | PostgreSQL schema name |

The schema must contain the tables defined in `schema-postgres.sql`.

**Example:**
```yaml
index:
  persistence:
    schema: e4s_index
```

---

### index.persistence.batch-size

| Type | Default | Description |
|------|---------|-------------|
| Integer | `1000` | Number of records per batch insert |

Larger batches = fewer database round trips = better performance, but more memory usage during batch operations.

**Example:**
```yaml
index:
  persistence:
    batch-size: 5000
```

---

### index.persistence.async-write

| Value | Description |
|-------|-------------|
| `false` | Synchronous writes to PostgreSQL |
| `true` (default) | Asynchronous writes to PostgreSQL |

When enabled, writes to PostgreSQL happen in a background thread. This ensures the API response is not blocked by database writes.

**Trade-offs:**
- `true`: Faster API responses, but slight risk of losing recent writes if both Redis and PostgreSQL fail
- `false`: Slower API responses, but stronger consistency guarantees

**Example:**
```yaml
index:
  persistence:
    async-write: true
```

---

### index.persistence.flush-interval-ms

| Type | Default | Description |
|------|---------|-------------|
| Long | `100` | Redis flush interval in milliseconds |

This controls the Redis write-behind interval. Not specific to PostgreSQL, but works together with persistence.

**Example:**
```yaml
index:
  persistence:
    flush-interval-ms: 100
```

---

## API Endpoints

### Reindex Endpoints

When persistence is enabled, these admin endpoints become available:

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/admin/index/{indexName}/reindex` | Full reindex from PostgreSQL |
| POST | `/api/v1/admin/index/{indexName}/reindex/partition?partition=X&granularity=DAY` | Partition reindex |
| GET | `/api/v1/admin/index/{indexName}/reindex/status` | Get reindex status |

---

## Database Schema

### Tables Created

```sql
-- Schema
CREATE SCHEMA e4s_index;

-- Index data table
CREATE TABLE e4s_index.meter_index (
    id BIGSERIAL PRIMARY KEY,
    index_name VARCHAR(100) NOT NULL,
    entity_id BIGINT NOT NULL,
    granularity VARCHAR(10) NOT NULL,
    epoch_value INT NOT NULL,
    partition_num INT NOT NULL,
    created_at TIMESTAMP DEFAULT NOW(),
    
    UNIQUE(index_name, entity_id, granularity, epoch_value)
);

-- Indexes
CREATE INDEX idx_meter_index_lookup 
    ON e4s_index.meter_index (index_name, entity_id, granularity, epoch_value);

CREATE INDEX idx_meter_index_partition 
    ON e4s_index.meter_index (index_name, entity_id, granularity, partition_num);

-- Reindex status
CREATE TABLE e4s_index.reindex_status (
    id BIGSERIAL PRIMARY KEY,
    index_name VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL,
    ...
);
```

---

## Data Flow

### Write Operation

```
1. Client calls POST /api/v1/index/mark
2. IndexService.mark()
   ├── Write to Redis (synchronous)
   └── Write to PostgreSQL (async or sync)
3. Return response to client
```

### Read Operation

```
1. Client calls POST /api/v1/index/exists
2. IndexService.exists()
   └── Read from Redis cache only
```

### Reindex Operation

```
1. Admin calls POST /api/v1/admin/index/{name}/reindex
2. ReindexService reindexFull()
   ├── Query PostgreSQL for all entities
   └── Batch write to Redis
3. Return status
```

---

## Troubleshooting

### PostgreSQL connection fails

Check:
1. PostgreSQL is running: `docker-compose ps`
2. Connection string is correct: `jdbc:postgresql://host:port/dbname`
3. Credentials are correct

### Reindex is slow

1. Increase `batch-size` in configuration
2. Check PostgreSQL connection pool settings
3. Monitor database disk I/O

### Data mismatch between Redis and PostgreSQL

1. Check logs for PostgreSQL write failures
2. Run full reindex: `POST /api/v1/admin/index/{name}/reindex`
3. Consider using synchronous writes (`async-write: false`)

---

## Security Considerations

1. **Credentials**: Use environment variables or secrets management
2. **Network**: PostgreSQL should be on internal network
3. **SSL**: Enable SSL for production connections
4. **Permissions**: Use least-privilege user for application
