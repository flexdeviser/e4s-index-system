package com.e4s.index.benchmark;

import com.e4s.index.model.Granularity;
import com.e4s.index.model.TimeIndex;
import com.e4s.index.util.TimeUtils;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 3)
@Fork(1)
public class IndexBenchmark {

    private static final String INDEX_NAME = "meter_pq_index";
    private static final int METER_COUNT = 100;
    private static final int DAYS_IN_YEAR = 365;
    private static final int YEAR = 2025;

    private Map<Long, TimeIndex> dayIndex;
    private Map<Long, TimeIndex> monthIndex;
    private Map<Long, TimeIndex> yearIndex;

    private Random random;
    private long[] meterIds;
    private long yearStartMillis;
    private long yearEndMillis;

    @Setup(Level.Trial)
    public void setup() {
        dayIndex = new HashMap<>();
        monthIndex = new HashMap<>();
        yearIndex = new HashMap<>();
        random = new Random(42);
        meterIds = new long[METER_COUNT];

        yearStartMillis = LocalDate.of(YEAR, 1, 1)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli();
        yearEndMillis = LocalDate.of(YEAR, 12, 31)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli();

        System.out.println("=== Setting up benchmark data ===");
        System.out.println("Index name: " + INDEX_NAME);
        System.out.println("Meters: " + METER_COUNT);
        System.out.println("Days per meter: " + DAYS_IN_YEAR);
        System.out.println("Year: " + YEAR);

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < METER_COUNT; i++) {
            long meterId = i + 1;
            meterIds[i] = meterId;

            TimeIndex dayTimeIndex = new TimeIndex();
            TimeIndex monthTimeIndex = new TimeIndex();
            TimeIndex yearTimeIndex = new TimeIndex();

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

            yearTimeIndex.add(TimeUtils.toEpochValue(yearStartMillis, "YEAR"));

            dayIndex.put(meterId, dayTimeIndex);
            monthIndex.put(meterId, monthTimeIndex);
            yearIndex.put(meterId, yearTimeIndex);
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

        System.out.println("=== Memory Usage ===");
        System.out.println("Used memory: " + formatBytes(usedMemory));
        System.out.println("Free memory: " + formatBytes(freeMemory));
        System.out.println("Total memory: " + formatBytes(totalMemory));
        System.out.println("Max memory: " + formatBytes(runtime.maxMemory()));

        long indexMemory = 0;
        for (TimeIndex index : dayIndex.values()) {
            indexMemory += index.sizeInBytes();
        }
        for (TimeIndex index : monthIndex.values()) {
            indexMemory += index.sizeInBytes();
        }
        for (TimeIndex index : yearIndex.values()) {
            indexMemory += index.sizeInBytes();
        }

        System.out.println();
        System.out.println("Index memory (RoaringBitmap): " + formatBytes(indexMemory));
        System.out.println("Per meter (day index): " + formatBytes(dayIndex.values().iterator().next().sizeInBytes()));
        System.out.println("Total entries: " + (METER_COUNT * DAYS_IN_YEAR) + " day entries");
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.2f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    private long randomTimestampInYear() {
        long range = yearEndMillis - yearStartMillis;
        return yearStartMillis + (long) (random.nextDouble() * range);
    }

    private long randomMeterId() {
        return meterIds[random.nextInt(METER_COUNT)];
    }

    @Benchmark
    public void existsDay(Blackhole bh) {
        long meterId = randomMeterId();
        long timestamp = randomTimestampInYear();
        TimeIndex index = dayIndex.get(meterId);
        int dayValue = TimeUtils.toEpochValue(timestamp, "DAY");
        bh.consume(index.contains(dayValue));
    }

    @Benchmark
    public void existsMonth(Blackhole bh) {
        long meterId = randomMeterId();
        long timestamp = randomTimestampInYear();
        TimeIndex index = monthIndex.get(meterId);
        int monthValue = TimeUtils.toEpochValue(timestamp, "MONTH");
        bh.consume(index.contains(monthValue));
    }

    @Benchmark
    public void existsYear(Blackhole bh) {
        long meterId = randomMeterId();
        long timestamp = randomTimestampInYear();
        TimeIndex index = yearIndex.get(meterId);
        int yearValue = TimeUtils.toEpochValue(timestamp, "YEAR");
        bh.consume(index.contains(yearValue));
    }

    @Benchmark
    public void findPrevDay(Blackhole bh) {
        long meterId = randomMeterId();
        long timestamp = randomTimestampInYear();
        TimeIndex index = dayIndex.get(meterId);
        int dayValue = TimeUtils.toEpochValue(timestamp, "DAY");
        bh.consume(index.findPrev(dayValue));
    }

    @Benchmark
    public void findNextDay(Blackhole bh) {
        long meterId = randomMeterId();
        long timestamp = randomTimestampInYear();
        TimeIndex index = dayIndex.get(meterId);
        int dayValue = TimeUtils.toEpochValue(timestamp, "DAY");
        bh.consume(index.findNext(dayValue));
    }

    @Benchmark
    public void findPrevMonth(Blackhole bh) {
        long meterId = randomMeterId();
        long timestamp = randomTimestampInYear();
        TimeIndex index = monthIndex.get(meterId);
        int monthValue = TimeUtils.toEpochValue(timestamp, "MONTH");
        bh.consume(index.findPrev(monthValue));
    }

    @Benchmark
    public void findNextMonth(Blackhole bh) {
        long meterId = randomMeterId();
        long timestamp = randomTimestampInYear();
        TimeIndex index = monthIndex.get(meterId);
        int monthValue = TimeUtils.toEpochValue(timestamp, "MONTH");
        bh.consume(index.findNext(monthValue));
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        System.out.println();
        System.out.println("=== Benchmark completed ===");
        printMemoryUsage();
    }
}
