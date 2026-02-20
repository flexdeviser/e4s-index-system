package com.e4s.index.util;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.*;

class TimeUtilsTest {

    @Test
    void testToDayEpoch() {
        long timestamp = 1704067200000L; // 2024-01-01 00:00:00 UTC
        int dayEpoch = TimeUtils.toDayEpoch(timestamp);
        long expectedDays = ChronoUnit.DAYS.between(LocalDate.of(1970, 1, 1), LocalDate.of(2024, 1, 1));
        assertEquals((int) expectedDays, dayEpoch);
    }

    @Test
    void testToMonthEpoch() {
        long timestamp = 1704067200000L; // 2024-01-01 00:00:00 UTC
        int monthEpoch = TimeUtils.toMonthEpoch(timestamp);
        // January 2024 = (2024 - 1970) * 12 + 0 = 648
        assertEquals(648, monthEpoch);
    }

    @Test
    void testToYearEpoch() {
        long timestamp = 1704067200000L; // 2024-01-01 00:00:00 UTC
        int yearEpoch = TimeUtils.toYearEpoch(timestamp);
        assertEquals(54, yearEpoch); // 2024 - 1970
    }

    @Test
    void testToEpochValueDay() {
        long timestamp = 1704067200000L;
        int value = TimeUtils.toEpochValue(timestamp, "DAY");
        long expectedDays = ChronoUnit.DAYS.between(LocalDate.of(1970, 1, 1), LocalDate.of(2024, 1, 1));
        assertEquals((int) expectedDays, value);
    }

    @Test
    void testToEpochValueMonth() {
        long timestamp = 1704067200000L;
        int value = TimeUtils.toEpochValue(timestamp, "MONTH");
        assertEquals(648, value);
    }

    @Test
    void testToEpochValueYear() {
        long timestamp = 1704067200000L;
        int value = TimeUtils.toEpochValue(timestamp, "YEAR");
        assertEquals(54, value);
    }

    @Test
    void testDayEpochToMillis() {
        int dayEpoch = (int) ChronoUnit.DAYS.between(LocalDate.of(1970, 1, 1), LocalDate.of(2024, 1, 1));
        long millis = TimeUtils.dayEpochToMillis(dayEpoch);
        assertEquals(1704067200000L, millis);
    }

    @Test
    void testMonthEpochToMillis() {
        int monthEpoch = 648; // January 2024
        long millis = TimeUtils.monthEpochToMillis(monthEpoch);
        assertEquals(1704067200000L, millis);
    }

    @Test
    void testYearEpochToMillis() {
        int yearEpoch = 54; // 2024
        long millis = TimeUtils.yearEpochToMillis(yearEpoch);
        assertEquals(1704067200000L, millis);
    }

    @Test
    void testRoundTripDay() {
        long original = 1704067200000L;
        int dayEpoch = TimeUtils.toDayEpoch(original);
        long result = TimeUtils.dayEpochToMillis(dayEpoch);
        assertEquals(original, result);
    }

    @Test
    void testRoundTripMonth() {
        long original = 1704067200000L; // January 2024
        int monthEpoch = TimeUtils.toMonthEpoch(original);
        long result = TimeUtils.monthEpochToMillis(monthEpoch);
        assertEquals(original, result);
    }

    @Test
    void testRoundTripYear() {
        long original = 1704067200000L; // 2024
        int yearEpoch = TimeUtils.toYearEpoch(original);
        long result = TimeUtils.yearEpochToMillis(yearEpoch);
        assertEquals(original, result);
    }

    @Test
    void testMonthEpochForVariousMonths() {
        // Test December 2023
        long dec2023 = LocalDate.of(2023, 12, 1)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli();
        assertEquals(647, TimeUtils.toMonthEpoch(dec2023));

        // Test June 2024
        long jun2024 = LocalDate.of(2024, 6, 1)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli();
        assertEquals(653, TimeUtils.toMonthEpoch(jun2024));
    }
}
