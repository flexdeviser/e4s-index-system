package com.e4s.index.benchmark;

import com.e4s.index.config.IndexProperties;
import com.e4s.index.model.Granularity;
import com.e4s.index.repository.IndexRepository;
import com.e4s.index.repository.PostgresIndexRepository;
import com.e4s.index.service.impl.IndexServiceImpl;
import com.e4s.index.util.TimeUtils;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Random;

public class PostgresBenchmark {

    private static final String INDEX_NAME = "meter_pg_index";
    private static final int METER_COUNT = 100;
    private static final int DAYS_IN_YEAR = 365;
    private static final int YEAR = 2025;
    private static final int WARMUP_ITERATIONS = 3;
    private static final int MEASUREMENT_ITERATIONS = 5;
    private static final int OPERATIONS_PER_ITERATION = 10_000;
    private static final int SINGLE_METER_UPDATES = 100_000;

    private IndexServiceImpl indexService;
    private long[] meterIds;
    private long yearStartMillis;
    private long yearEndMillis;
    private Random random;

    public static void main(String[] args) {
        PostgresBenchmark benchmark = new PostgresBenchmark();
        benchmark.setup();
        benchmark.run();
    }

    private void setup() {
        String redisHost = System.getenv().getOrDefault("REDIS_HOST", "localhost");
        int redisPort = Integer.parseInt(System.getenv().getOrDefault("REDIS_PORT", "6379"));
        
        String pgHost = System.getenv().getOrDefault("POSTGRES_HOST", "localhost");
        int pgPort = Integer.parseInt(System.getenv().getOrDefault("POSTGRES_PORT", "5432"));
        String pgDb = System.getenv().getOrDefault("POSTGRES_DB", "meterdb");
        String pgUser = System.getenv().getOrDefault("POSTGRES_USER", "postgres");
        String pgPassword = System.getenv().getOrDefault("POSTGRES_PASSWORD", "postgres");
        
        boolean pgEnabled = Boolean.parseBoolean(System.getenv().getOrDefault("POSTGRES_ENABLED", "true"));
        boolean pgAsync = Boolean.parseBoolean(System.getenv().getOrDefault("POSTGRES_ASYNC", "true"));

        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║      E4S Index System - PostgreSQL Benchmark             ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.println("Configuration:");
        System.out.println("  Redis host:      " + redisHost + ":" + redisPort);
        System.out.println("  PostgreSQL:      " + pgHost + ":" + pgPort + "/" + pgDb);
        System.out.println("  PostgreSQL:      enabled=" + pgEnabled + ", async=" + pgAsync);
        System.out.println("  Index name:      " + INDEX_NAME);
        System.out.println("  Meters:          " + METER_COUNT);
        System.out.println("  Days per meter:  " + DAYS_IN_YEAR);
        System.out.println("  Year:            " + YEAR);
        System.out.println();

        RedisStandaloneConfiguration redisConfig = new RedisStandaloneConfiguration(redisHost, redisPort);
        LettuceConnectionFactory factory = new LettuceConnectionFactory(redisConfig);
        factory.afterPropertiesSet();

        RedisTemplate<String, byte[]> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(factory);
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setValueSerializer(RedisSerializer.byteArray());
        redisTemplate.setHashKeySerializer(new StringRedisSerializer());
        redisTemplate.setHashValueSerializer(RedisSerializer.byteArray());
        redisTemplate.afterPropertiesSet();

        IndexRepository pgRepository = null;
        if (pgEnabled) {
            DriverManagerDataSource dataSource = new DriverManagerDataSource();
            dataSource.setDriverClassName("org.postgresql.Driver");
            dataSource.setUrl("jdbc:postgresql://" + pgHost + ":" + pgPort + "/" + pgDb);
            dataSource.setUsername(pgUser);
            dataSource.setPassword(pgPassword);
            
            JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
            
            IndexProperties props = new IndexProperties();
            IndexProperties.Persistence persistence = new IndexProperties.Persistence();
            persistence.setEnabled(true);
            persistence.setSchema("e4s_index");
            persistence.setBatchSize(1000);
            persistence.setAsyncWrite(pgAsync);
            props.setPersistence(persistence);
            
            pgRepository = new PostgresIndexRepository(jdbcTemplate, props);
            
            pgRepository.deleteByIndexName(INDEX_NAME);
        }

        if (pgEnabled) {
            indexService = new IndexServiceImpl(redisTemplate, 100000, 100, pgRepository, pgAsync);
        } else {
            indexService = new IndexServiceImpl(redisTemplate, 100000, 100);
        }

        meterIds = new long[METER_COUNT];
        random = new Random(42);

        yearStartMillis = LocalDate.of(YEAR, 1, 1)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli();
        yearEndMillis = LocalDate.of(YEAR, 12, 31)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli();

        System.out.println("Creating index and populating data...");

        long startTime = System.currentTimeMillis();

        indexService.createIndex(INDEX_NAME);

        for (int i = 0; i < METER_COUNT; i++) {
            long meterId = i + 1;
            meterIds[i] = meterId;

            long[] timestamps = new long[DAYS_IN_YEAR];
            for (int day = 0; day < DAYS_IN_YEAR; day++) {
                LocalDate date = LocalDate.of(YEAR, 1, 1).plusDays(day);
                timestamps[day] = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
            }

            int[] dayValues = new int[DAYS_IN_YEAR];
            for (int day = 0; day < DAYS_IN_YEAR; day++) {
                dayValues[day] = TimeUtils.toEpochValue(timestamps[day], "DAY");
            }
            indexService.markBatch(INDEX_NAME, meterId, Granularity.DAY, dayValues);

            int[] monthValues = new int[12];
            for (int month = 1; month <= 12; month++) {
                LocalDate date = LocalDate.of(YEAR, month, 1);
                long timestamp = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
                monthValues[month - 1] = TimeUtils.toEpochValue(timestamp, "MONTH");
            }
            indexService.markBatch(INDEX_NAME, meterId, Granularity.MONTH, monthValues);

            if ((i + 1) % 20 == 0) {
                System.out.println("  Populated " + (i + 1) + "/" + METER_COUNT + " meters...");
            }
        }

        if (pgEnabled && pgAsync) {
            System.out.println("Waiting for async PostgreSQL writes to complete...");
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        long setupTime = System.currentTimeMillis();

        System.out.println();
        System.out.println("Setup completed in " + setupTime + " ms");
        System.out.println("Total entries: " + (METER_COUNT * DAYS_IN_YEAR));
        System.out.println();

        printStats();
        
        if (pgEnabled) {
            verifyPgPersistence();
        }
    }

    private void verifyPgPersistence() {
        System.out.println("Verifying PostgreSQL persistence...");
        try {
            IndexRepository repo = indexService.getPgRepository();
            if (repo != null) {
                long count = repo.countByIndexName(INDEX_NAME);
                System.out.println("  PostgreSQL entries: " + count);
                if (count == 0) {
                    System.out.println("  WARNING: No data found in PostgreSQL!");
                }
            }
        } catch (Exception e) {
            System.out.println("  WARNING: Could not verify PostgreSQL: " + e.getMessage());
        }
    }

    private void printStats() {
        var stats = indexService.getStats(INDEX_NAME);
        System.out.println("Index Stats:");
        System.out.println("  Entity count:     " + stats.entityCount());
        System.out.println("  Cache size:      " + stats.cacheSize());
        System.out.println("  Memory usage:    " + formatBytes(stats.memoryUsageBytes()));
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.2f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private void run() {
        System.out.println();
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║                    Benchmark Results                       ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.println("Running " + MEASUREMENT_ITERATIONS + " iterations x " + OPERATIONS_PER_ITERATION + " ops each");
        System.out.println();

        benchmarkOperation("existsDay", Granularity.DAY, "exists");
        benchmarkOperation("existsMonth", Granularity.MONTH, "exists");
        benchmarkOperation("findPrevDay", Granularity.DAY, "prev");
        benchmarkOperation("findNextDay", Granularity.DAY, "next");
        benchmarkOperation("findPrevMonth", Granularity.MONTH, "prev");
        benchmarkOperation("findNextMonth", Granularity.MONTH, "next");

        System.out.println("--- WRITE OPERATIONS ---");
        
        benchmarkWriteOperation("markSingle", Granularity.DAY, "markSingle");
        benchmarkWriteOperation("markBatch", Granularity.DAY, "markBatch");
        
        System.out.println();
        System.out.println("--- SINGLE METER CONTINUOUS UPDATE ---");
        
        benchmarkSingleMeterUpdate();

        System.out.println();
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║                  Benchmark Completed                       ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");
        System.out.println();
        printStats();
    }

    private void benchmarkOperation(String name, Granularity granularity, String operation) {
        Random localRandom = new Random(42);

        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            for (int j = 0; j < OPERATIONS_PER_ITERATION; j++) {
                executeOperation(granularity, operation, localRandom);
            }
        }

        long[] iterationTimes = new long[MEASUREMENT_ITERATIONS];

        for (int i = 0; i < MEASUREMENT_ITERATIONS; i++) {
            long start = System.nanoTime();
            for (int j = 0; j < OPERATIONS_PER_ITERATION; j++) {
                executeOperation(granularity, operation, localRandom);
            }
            long end = System.nanoTime();
            iterationTimes[i] = end - start;
        }

        double avgTimeNs = 0;
        for (long time : iterationTimes) {
            avgTimeNs += time;
        }
        avgTimeNs /= MEASUREMENT_ITERATIONS;

        double avgOpsPerSec = OPERATIONS_PER_ITERATION / (avgTimeNs / 1_000_000_000.0);
        double avgLatencyUs = (avgTimeNs / OPERATIONS_PER_ITERATION) / 1000.0;

        System.out.printf("  %-15s  %,.0f ops/sec  (%.2f μs/op)%n", name, avgOpsPerSec, avgLatencyUs);
    }

    private void executeOperation(Granularity granularity, String operation, Random random) {
        long meterId = meterIds[random.nextInt(METER_COUNT)];
        long timestamp = yearStartMillis + (long) (random.nextDouble() * (yearEndMillis - yearStartMillis));

        switch (operation) {
            case "exists" -> indexService.exists(INDEX_NAME, meterId, granularity, 
                    TimeUtils.toEpochValue(timestamp, granularity.name()));
            case "prev" -> indexService.findPrev(INDEX_NAME, meterId, granularity,
                    TimeUtils.toEpochValue(timestamp, granularity.name()));
            case "next" -> indexService.findNext(INDEX_NAME, meterId, granularity,
                    TimeUtils.toEpochValue(timestamp, granularity.name()));
        }
    }

    private void benchmarkWriteOperation(String name, Granularity granularity, String operation) {
        Random localRandom = new Random(42);

        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            for (int j = 0; j < OPERATIONS_PER_ITERATION / 10; j++) {
                executeWriteOperation(granularity, operation, localRandom);
            }
        }

        int opsCount = OPERATIONS_PER_ITERATION / 10;
        long[] iterationTimes = new long[MEASUREMENT_ITERATIONS];

        for (int i = 0; i < MEASUREMENT_ITERATIONS; i++) {
            long start = System.nanoTime();
            for (int j = 0; j < opsCount; j++) {
                executeWriteOperation(granularity, operation, localRandom);
            }
            long end = System.nanoTime();
            iterationTimes[i] = end - start;
        }

        double avgTimeNs = 0;
        for (long time : iterationTimes) {
            avgTimeNs += time;
        }
        avgTimeNs /= MEASUREMENT_ITERATIONS;

        double avgOpsPerSec = opsCount / (avgTimeNs / 1_000_000_000.0);
        double avgLatencyUs = (avgTimeNs / opsCount) / 1000.0;

        System.out.printf("  %-15s  %,.0f ops/sec  (%.2f μs/op)%n", name, avgOpsPerSec, avgLatencyUs);
    }

    private void executeWriteOperation(Granularity granularity, String operation, Random random) {
        long meterId = meterIds[random.nextInt(METER_COUNT)];
        
        switch (operation) {
            case "markSingle" -> {
                long timestamp = yearStartMillis + (long) (random.nextDouble() * (yearEndMillis - yearStartMillis));
                int value = TimeUtils.toEpochValue(timestamp, granularity.name());
                indexService.mark(INDEX_NAME, meterId, granularity, value);
            }
            case "markBatch" -> {
                int[] values = new int[10];
                for (int i = 0; i < 10; i++) {
                    long timestamp = yearStartMillis + (long) (random.nextDouble() * (yearEndMillis - yearStartMillis));
                    values[i] = TimeUtils.toEpochValue(timestamp, granularity.name());
                }
                indexService.markBatch(INDEX_NAME, meterId, granularity, values);
            }
        }
    }

    private void benchmarkSingleMeterUpdate() {
        Random localRandom = new Random(42);
        
        long singleMeterId = 1L;
        
        System.out.println("  Testing continuous updates to single meter (meterId=1)...");

        for (int i = 0; i < 1000; i++) {
            int dayValue = localRandom.nextInt(DAYS_IN_YEAR);
            indexService.mark(INDEX_NAME, singleMeterId, Granularity.DAY, dayValue);
        }

        int opsCount = SINGLE_METER_UPDATES;
        long start = System.nanoTime();
        for (int i = 0; i < opsCount; i++) {
            int dayValue = i % DAYS_IN_YEAR;
            indexService.mark(INDEX_NAME, singleMeterId, Granularity.DAY, dayValue);
        }
        long end = System.nanoTime();

        double avgTimeNs = (double) (end - start) / opsCount;
        double avgOpsPerSec = opsCount / ((end - start) / 1_000_000_000.0);

        System.out.printf("  %-15s  %,.0f ops/sec  (%.2f μs/op)%n", 
            "singleMeterUpdate", avgOpsPerSec, avgTimeNs / 1000.0);
    }
}
