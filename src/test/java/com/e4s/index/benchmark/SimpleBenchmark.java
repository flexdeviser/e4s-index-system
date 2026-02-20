package com.e4s.index.benchmark;

import com.e4s.index.model.TimeIndex;
import com.e4s.index.util.TimeUtils;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class SimpleBenchmark {

    private static final String INDEX_NAME = "meter_pq_index";
    private static final int METER_COUNT = 100;
    private static final int DAYS_IN_YEAR = 365;
    private static final int YEAR = 2025;
    private static final int WARMUP_ITERATIONS = 3;
    private static final int MEASUREMENT_ITERATIONS = 5;
    private static final int OPERATIONS_PER_ITERATION = 100_000;
    private static final int SINGLE_METER_UPDATES = 100_000;

    private Map<Long, TimeIndex> dayIndex;
    private Map<Long, TimeIndex> monthIndex;
    private long[] meterIds;
    private long yearStartMillis;
    private long yearEndMillis;

    public static void main(String[] args) {
        SimpleBenchmark benchmark = new SimpleBenchmark();
        benchmark.setup();
        benchmark.run();
    }

    private void setup() {
        dayIndex = new HashMap<>();
        monthIndex = new HashMap<>();
        meterIds = new long[METER_COUNT];

        yearStartMillis = LocalDate.of(YEAR, 1, 1)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli();
        yearEndMillis = LocalDate.of(YEAR, 12, 31)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli();

        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║              E4S Index System Benchmark                    ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.println("Configuration:");
        System.out.println("  Index name:    " + INDEX_NAME);
        System.out.println("  Meters:        " + METER_COUNT);
        System.out.println("  Days per meter: " + DAYS_IN_YEAR);
        System.out.println("  Year:          " + YEAR);
        System.out.println("  Total entries: " + (METER_COUNT * DAYS_IN_YEAR));
        System.out.println();

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < METER_COUNT; i++) {
            long meterId = i + 1;
            meterIds[i] = meterId;

            TimeIndex dayTimeIndex = new TimeIndex();
            TimeIndex monthTimeIndex = new TimeIndex();

            for (int day = 0; day < DAYS_IN_YEAR; day++) {
                LocalDate date = LocalDate.of(YEAR, 1, 1).plusDays(day);
                long timestamp = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
                dayTimeIndex.add(TimeUtils.toEpochValue(timestamp, "DAY"));
            }

            for (int month = 1; month <= 12; month++) {
                LocalDate date = LocalDate.of(YEAR, month, 1);
                long timestamp = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
                monthTimeIndex.add(TimeUtils.toEpochValue(timestamp, "MONTH"));
            }

            dayIndex.put(meterId, dayTimeIndex);
            monthIndex.put(meterId, monthTimeIndex);
        }

        long setupTime = System.currentTimeMillis() - startTime;

        System.out.println("Setup completed in " + setupTime + " ms");
        System.out.println();
        printMemoryUsage();
        System.out.println();
    }

    private void printMemoryUsage() {
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;

        System.out.println("Memory Usage:");
        System.out.println("  Heap used:   " + formatBytes(usedMemory));
        System.out.println("  Heap total:  " + formatBytes(totalMemory));
        System.out.println("  Heap max:    " + formatBytes(runtime.maxMemory()));

        long indexMemory = 0;
        for (TimeIndex index : dayIndex.values()) {
            indexMemory += index.sizeInBytes();
        }
        for (TimeIndex index : monthIndex.values()) {
            indexMemory += index.sizeInBytes();
        }

        System.out.println();
        System.out.println("Index Storage (RoaringBitmap):");
        System.out.println("  Total index memory:  " + formatBytes(indexMemory));
        System.out.println("  Per meter (day):     " + formatBytes(dayIndex.values().iterator().next().sizeInBytes()));
        System.out.println("  Per meter (month):   " + formatBytes(monthIndex.values().iterator().next().sizeInBytes()));
        System.out.println("  Bytes per entry:     " + String.format("%.2f", (double) indexMemory / (METER_COUNT * DAYS_IN_YEAR)));
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
        System.out.println("--- READ OPERATIONS ---");

        benchmarkOperation("existsDay", this::existsDay);
        benchmarkOperation("existsMonth", this::existsMonth);
        benchmarkOperation("findPrevDay", this::findPrevDay);
        benchmarkOperation("findNextDay", this::findNextDay);
        benchmarkOperation("findPrevMonth", this::findPrevMonth);
        benchmarkOperation("findNextMonth", this::findNextMonth);

        System.out.println();
        System.out.println("--- WRITE OPERATIONS ---");

        benchmarkOperation("markSingle", this::markSingle);
        benchmarkOperation("markBatch", this::markBatch);

        System.out.println();
        System.out.println("--- SINGLE METER CONTINUOUS UPDATE ---");

        benchmarkSingleMeterUpdate();

        System.out.println();
        System.out.println("╔════════════════════════════════════════════════════════════╗");
        System.out.println("║                  Benchmark Completed                       ║");
        System.out.println("╚════════════════════════════════════════════════════════════╝");
        printMemoryUsage();
    }

    private void benchmarkOperation(String name, Runnable operation) {
        Random random = new Random(42);

        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            for (int j = 0; j < OPERATIONS_PER_ITERATION; j++) {
                operation.run();
            }
        }

        long totalTime = 0;
        long[] iterationTimes = new long[MEASUREMENT_ITERATIONS];

        for (int i = 0; i < MEASUREMENT_ITERATIONS; i++) {
            long start = System.nanoTime();
            for (int j = 0; j < OPERATIONS_PER_ITERATION; j++) {
                operation.run();
            }
            long end = System.nanoTime();
            iterationTimes[i] = end - start;
            totalTime += iterationTimes[i];
        }

        double avgTimeNs = (double) totalTime / MEASUREMENT_ITERATIONS;
        double avgOpsPerSec = OPERATIONS_PER_ITERATION / (avgTimeNs / 1_000_000_000.0);

        System.out.printf("  %-15s  %,.0f ops/sec%n", name, avgOpsPerSec);
    }

    private void benchmarkSingleMeterUpdate() {
        Random random = new Random(42);
        
        // Use a dedicated meter for this benchmark
        long singleMeterId = 1L;
        TimeIndex singleDayIndex = dayIndex.get(singleMeterId);
        long initialSize = singleDayIndex.sizeInBytes();
        
        System.out.println("  Initial bitmap size: " + formatBytes(initialSize));

        // Warmup
        for (int i = 0; i < 1000; i++) {
            int dayValue = random.nextInt(DAYS_IN_YEAR);
            singleDayIndex.add(dayValue);
        }

        // Reset
        singleDayIndex = new TimeIndex();
        for (int day = 0; day < DAYS_IN_YEAR; day++) {
            singleDayIndex.add(day);
        }
        dayIndex.put(singleMeterId, singleDayIndex);

        // Benchmark continuous updates to single meter
        long start = System.nanoTime();
        for (int i = 0; i < SINGLE_METER_UPDATES; i++) {
            // Simulate adding new days (some may already exist)
            int baseDay = (i % DAYS_IN_YEAR);
            singleDayIndex.add(baseDay);
        }
        long end = System.nanoTime();

        double avgTimeNs = (double) (end - start) / SINGLE_METER_UPDATES;
        double avgOpsPerSec = SINGLE_METER_UPDATES / ((end - start) / 1_000_000_000.0);

        System.out.printf("  %-15s  %,.0f ops/sec (%.2f μs/op)%n", 
            "singleMeterUpdate", avgOpsPerSec, avgTimeNs / 1000.0);
        
        long finalSize = singleDayIndex.sizeInBytes();
        System.out.println("  Final bitmap size:  " + formatBytes(finalSize));
    }

    private Random threadLocalRandom = new Random();

    private void existsDay() {
        long meterId = meterIds[threadLocalRandom.nextInt(METER_COUNT)];
        long timestamp = yearStartMillis + (long) (threadLocalRandom.nextDouble() * (yearEndMillis - yearStartMillis));
        TimeIndex index = dayIndex.get(meterId);
        int dayValue = TimeUtils.toEpochValue(timestamp, "DAY");
        index.contains(dayValue);
    }

    private void existsMonth() {
        long meterId = meterIds[threadLocalRandom.nextInt(METER_COUNT)];
        long timestamp = yearStartMillis + (long) (threadLocalRandom.nextDouble() * (yearEndMillis - yearStartMillis));
        TimeIndex index = monthIndex.get(meterId);
        int monthValue = TimeUtils.toEpochValue(timestamp, "MONTH");
        index.contains(monthValue);
    }

    private void findPrevDay() {
        long meterId = meterIds[threadLocalRandom.nextInt(METER_COUNT)];
        long timestamp = yearStartMillis + (long) (threadLocalRandom.nextDouble() * (yearEndMillis - yearStartMillis));
        TimeIndex index = dayIndex.get(meterId);
        int dayValue = TimeUtils.toEpochValue(timestamp, "DAY");
        index.findPrev(dayValue);
    }

    private void findNextDay() {
        long meterId = meterIds[threadLocalRandom.nextInt(METER_COUNT)];
        long timestamp = yearStartMillis + (long) (threadLocalRandom.nextDouble() * (yearEndMillis - yearStartMillis));
        TimeIndex index = dayIndex.get(meterId);
        int dayValue = TimeUtils.toEpochValue(timestamp, "DAY");
        index.findNext(dayValue);
    }

    private void findPrevMonth() {
        long meterId = meterIds[threadLocalRandom.nextInt(METER_COUNT)];
        long timestamp = yearStartMillis + (long) (threadLocalRandom.nextDouble() * (yearEndMillis - yearStartMillis));
        TimeIndex index = monthIndex.get(meterId);
        int monthValue = TimeUtils.toEpochValue(timestamp, "MONTH");
        index.findPrev(monthValue);
    }

    private void findNextMonth() {
        long meterId = meterIds[threadLocalRandom.nextInt(METER_COUNT)];
        long timestamp = yearStartMillis + (long) (threadLocalRandom.nextDouble() * (yearEndMillis - yearStartMillis));
        TimeIndex index = monthIndex.get(meterId);
        int monthValue = TimeUtils.toEpochValue(timestamp, "MONTH");
        index.findNext(monthValue);
    }

    private void markSingle() {
        long meterId = meterIds[threadLocalRandom.nextInt(METER_COUNT)];
        long timestamp = yearStartMillis + (long) (threadLocalRandom.nextDouble() * (yearEndMillis - yearStartMillis));
        TimeIndex index = dayIndex.get(meterId);
        int dayValue = TimeUtils.toEpochValue(timestamp, "DAY");
        index.add(dayValue);
    }

    private void markBatch() {
        long meterId = meterIds[threadLocalRandom.nextInt(METER_COUNT)];
        int[] values = new int[10];
        for (int i = 0; i < 10; i++) {
            long timestamp = yearStartMillis + (long) (threadLocalRandom.nextDouble() * (yearEndMillis - yearStartMillis));
            values[i] = TimeUtils.toEpochValue(timestamp, "DAY");
        }
        TimeIndex index = dayIndex.get(meterId);
        index.addAll(values);
    }
}
