# E4S Index System - Use Cases

## Overview

E4S Index System is a time-series data existence index service. It answers the question: **"Does data exist for this meter at this time?"**

---

## Use Case Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        E4S INDEX SYSTEM USE CASES                          │
└─────────────────────────────────────────────────────────────────────────────┘

    ┌──────────────┐     ┌──────────────┐     ┌──────────────┐
    │   Meter      │     │    Batch     │     │   Admin/     │
    │   Data       │     │   Processing │     │   Monitoring  │
    │   Producer   │     │   Pipeline   │     │   Dashboard   │
    └──────┬───────┘     └──────┬───────┘     └──────┬───────┘
           │                    │                    │
           │ mark()             │ markBatch()       │ getStats()
           │                    │                    │
           ▼                    ▼                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                         E4S INDEX SERVICE                                   │
│                                                                             │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐      │
│  │ Create Index│  │   Mark      │  │    Query    │  │   Stats     │      │
│  │             │  │   Time      │  │   Existence │  │   Monitor   │      │
│  └─────────────┘  └─────────────┘  └─────────────┘  └─────────────┘      │
└─────────────────────────────────────────────────────────────────────────────┘
           │                    │                    │
           │                    │                    │
           ▼                    ▼                    ▼
    ┌──────────────┐     ┌──────────────┐     ┌──────────────┐
    │     Redis    │     │  PostgreSQL  │     │    REST     │
    │   (Cache)   │     │ (Persistence)│     │    API      │
    └──────────────┘     └──────────────┘     └──────────────┘
```

---

## Detailed Use Cases

### 1. Data Gap Detection

**Actor:** Batch Processing Pipeline

**Scenario:**
```
┌────────────────────────────────────────────────────────────────┐
│              GAP DETECTION USE CASE                            │
└────────────────────────────────────────────────────────────────┘

1. Pipeline receives meter data for processing
2. For each meter, check: "Have we processed this day before?"
3. If NOT exists → Process the data
4. If exists → Skip (already processed)

Flow:
┌──────────┐     exists()      ┌────────────┐     no      ┌─────────────┐
│  Batch   │ ───────────────▶  │   E4S      │ ──────────▶ │  Process   │
│ Pipeline │                   │   Index    │             │   Data     │
└──────────┘                   └────────────┘             └─────────────┘
                                  │ yes
                                  ▼
                            ┌────────────┐
                            │   Skip     │
                            │   Data     │
                            └────────────┘

API Call:
POST /api/v1/index/exists
{
  "indexName": "daily-meter-data",
  "entityId": 12345,
  "granularity": "DAY",
  "timestamp": 1704067200000
}
```

**Benefits:**
- O(1) lookup instead of database query
- Millions of meters can be checked in seconds
- Reduces database load significantly

---

### 2. Continuous Data Ingestion

**Actor:** IoT Data Collector

**Scenario:**
```
┌────────────────────────────────────────────────────────────────┐
│           CONTINUOUS INGESTION USE CASE                         │
└────────────────────────────────────────────────────────────────┘

1. Receive meter reading with timestamp
2. Mark "this day has data" in index
3. Continue processing

Flow:
┌──────────┐     mark()        ┌────────────┐     ┌────────────┐
│   IoT    │ ──────────────▶  │   E4S      │ ──▶ │  Process   │
│ Collector│                   │   Index    │     │  Further   │
└──────────┘                   └────────────┘     └────────────┘
                                      │
                                      ▼
                               ┌────────────┐
                               │   Redis   │
                               │  (Cache)  │
                               └────────────┘

API Call:
POST /api/v1/index/mark
{
  "indexName": "meter-consumption",
  "entityId": 12345,
  "granularity": "DAY",
  "timestamp": 1704067200000
}

Batch API:
POST /api/v1/index/markBatch
{
  "indexName": "meter-consumption",
  "entityId": 12345,
  "granularity": "DAY",
  "timestamps": [1704067200000, 1704153600000, ...]
}
```

**Benefits:**
- Ultra-fast marking (~1.5M ops/sec)
- Async persistence option for high throughput
- Redis handles millions of writes/second

---

### 3. Finding Previous/Next Data Points

**Actor:** Analytics Query Service

**Scenario:**
```
┌────────────────────────────────────────────────────────────────┐
│        FIND PREVIOUS/NEXT USE CASE                             │
└────────────────────────────────────────────────────────────────┘

1. User queries: "Show me data before June 15, 2025"
2. System finds the closest previous date with data
3. Returns that date for navigation

Flow:
┌──────────┐   findPrev()     ┌────────────┐     ┌────────────┐
│  User    │ ──────────────▶  │   E4S      │ ──▶ │  Return    │
│   App    │                   │   Index    │     │  Date      │
└──────────┘                   └────────────┘     └────────────┘

API Call:
POST /api/v1/index/prev
{
  "indexName": "meter-consumption",
  "entityId": 12345,
  "granularity": "DAY",
  "timestamp": 1704067200000
}

Response:
{
  "indexName": "meter-consumption",
  "entityId": 12345,
  "granularity": "DAY",
  "value": 1703980800    // Previous day with data
}

Use Cases:
- Pagination through historical data
- Finding data gaps
- Time range queries
- Calendar navigation in UI
```

---

### 4. Data Recovery / Cold Start

**Actor:** System Administrator

**Scenario:**
```
┌────────────────────────────────────────────────────────────────┐
│           COLD START / RECOVERY USE CASE                      │
└────────────────────────────────────────────────────────────────┘

1. System restarts after maintenance
2. Redis cache is empty (cold)
3. First query loads data from PostgreSQL
4. Subsequent queries use fast Redis cache

Flow:
┌──────────┐                    ┌────────────┐     ┌────────────┐
│ Service  │ ───────────────▶  │   E4S      │ ──▶ │   Check   │
│ Starts   │                    │   Index    │     │   Redis   │
└──────────┘                    └────────────┘     └────────────┘
                                                          │
                              ┌───────────────────────────┘
                              │ Cache Miss
                              ▼
                         ┌────────────┐     ┌────────────┐
                         │ PostgreSQL│ ──▶ │  Load &    │
                         │  (Disk)   │     │  Cache     │
                         └────────────┘     └────────────┘

Behavior:
- indexExists() → checks Redis first, then PostgreSQL
- exists() → loads from PostgreSQL if not in cache
- Data automatically cached in Redis after first access
- No manual reindexing needed!
```

---

### 5. Multi-Tenant Index Management

**Actor:** Platform Admin

**Scenario:**
```
┌────────────────────────────────────────────────────────────────�│
│           MULTI-TENANT MANAGEMENT USE CASE                    │
└────────────────────────────────────────────────────────────────┘

Each tenant has isolated indexes:

┌─────────────────────────────────────────────────────────────────┐
│                    TENANT A: "utility-company-a"                │
│  indexes: [meter-data, billing-cycle, payment-history]        │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│                    TENANT B: "utility-company-b"                │
│  indexes: [consumption, quality-metrics, alerts]               │
└─────────────────────────────────────────────────────────────────┘

API Calls:
- POST /api/v1/index        → Create index
- GET  /api/v1/index/{name} → Get index stats
- DELETE /api/v1/index/{name} → Delete index

Index Stats Response:
{
  "indexName": "meter-data",
  "entityCount": 150000,
  "cacheSize": 50000,
  "memoryUsage": "1.2 MB"
}
```

---

### 6. Persistence & Durability

**Actor:** Reliability Engineer

**Scenario:**
```
┌────────────────────────────────────────────────────────────────┐
│           PERSISTENCE CONFIGURATION USE CASE                    │
└────────────────────────────────────────────────────────────────┘

Configuration Options:

┌─────────────────────────────────────────────────────────────────┐
│  Mode 1: Cache Only (Fastest)                                 │
│  ┌──────────┐                                                   │
│  │   App    │ ──────▶ Redis (hot) ──────▶ [No persistence]     │
│  └──────────┘                                                   │
│  Use when: Data can be rebuilt from source                     │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│  Mode 2: Write-Through (Balanced)                              │
│  ┌──────────┐                                                   │
│  │   App    │ ──────▶ Redis ──────▶ PostgreSQL (sync)          │
│  └──────────┘                                                   │
│  Use when: Strong consistency needed                            │
│  Config: async-write: false                                    │
└─────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────┐
│  Mode 3: Write-Behind (Highest Throughput)                     │
│  ┌──────────┐                                                   │
│  │   App    │ ──────▶ Redis ───┬──▶ PostgreSQL (async)          │
│  └──────────┘                  │  (batch, configurable)       │
│                              ▼                                 │
│                         Queue (bounded)                         │
│  Use when: High write throughput needed                        │
│  Config: async-write: true                                     │
│          flush-interval-ms: 100                                │
└─────────────────────────────────────────────────────────────────┘
```

---

## Architecture Summary

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           SYSTEM ARCHITECTURE                               │
└─────────────────────────────────────────────────────────────────────────────┘

  ┌─────────────┐     ┌─────────────┐     ┌─────────────┐
  │   REST API  │────▶│ IndexService │────▶│   Redis     │
  │  (Spring)   │     │   (Core)    │     │  (Cache)    │
  └─────────────┘     └─────────────┘     └─────────────┘
                             │
                             │ (optional)
                             ▼
                      ┌─────────────┐
                      │ PostgreSQL   │
                      │ (Persistence)│
                      └─────────────┘

Data Flow:
┌─────────────────────────────────────────────────────────────────┐
│  Write Path:                                                    │
│  mark() → LRU Cache → [Async Queue] → PostgreSQL               │
│                                                                 │
│  Read Path:                                                     │
│  exists() → LRU Cache → Redis → PostgreSQL (on miss)           │
└─────────────────────────────────────────────────────────────────┘
```

---

## Performance Characteristics

| Operation | Throughput | Latency | Use Case |
|-----------|------------|---------|----------|
| mark() | 1.5M ops/sec | 0.6 μs | Data ingestion |
| markBatch() | 400K ops/sec | 2.5 μs | Bulk ingestion |
| exists() | 1.9M ops/sec | 0.5 μs | Gap detection |
| findPrev/Next | 500K ops/sec | 2.0 μs | Navigation |

---

## Summary

| Use Case | Primary API | Performance |
|----------|-------------|-------------|
| Gap Detection | `exists()` | ~2M ops/sec |
| Data Ingestion | `mark()` | ~1.5M ops/sec |
| Bulk Import | `markBatch()` | ~400K ops/sec |
| Navigation | `findPrev/Next` | ~500K ops/sec |
| Cold Start | Auto-load | Transparent |
