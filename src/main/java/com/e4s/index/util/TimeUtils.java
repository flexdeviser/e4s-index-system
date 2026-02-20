package com.e4s.index.util;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;

/**
 * Utility class for time conversions between epoch milliseconds and compact integer representations.
 * 
 * <p>This class provides conversions for different time granularities:</p>
 * <ul>
 *   <li><b>DAY</b> - Days since Unix epoch (1970-01-01)</li>
 *   <li><b>MONTH</b> - Months since January 1970</li>
 *   <li><b>YEAR</b> - Years since 1970</li>
 * </ul>
 * 
 * <h2>Example Usage</h2>
 * <pre>{@code
 * long timestamp = 1704067200000L; // 2024-01-01 00:00:00 UTC
 * 
 * int dayEpoch = TimeUtils.toDayEpoch(timestamp);    // 19723
 * int monthEpoch = TimeUtils.toMonthEpoch(timestamp); // 648
 * int yearEpoch = TimeUtils.toYearEpoch(timestamp);   // 54
 * 
 * // Round-trip conversion
 * long millis = TimeUtils.dayEpochToMillis(dayEpoch); // 1704067200000
 * }</pre>
 * 
 * @author E4S Team
 * @version 1.0.0
 */
public final class TimeUtils {

    private static final int EPOCH_YEAR = 1970;

    private TimeUtils() {
    }

    /**
     * Converts epoch milliseconds to days since Unix epoch.
     *
     * @param epochMillis the epoch milliseconds
     * @return days since 1970-01-01
     */
    public static int toDayEpoch(long epochMillis) {
        return (int) (epochMillis / (24 * 60 * 60 * 1000));
    }

    /**
     * Converts epoch milliseconds to months since January 1970.
     *
     * @param epochMillis the epoch milliseconds
     * @return months since January 1970 (0 = Jan 1970)
     */
    public static int toMonthEpoch(long epochMillis) {
        Instant instant = Instant.ofEpochMilli(epochMillis);
        LocalDate date = LocalDate.ofInstant(instant, ZoneOffset.UTC);
        YearMonth yearMonth = YearMonth.from(date);
        return (yearMonth.getYear() - EPOCH_YEAR) * 12 + (yearMonth.getMonthValue() - 1);
    }

    /**
     * Converts epoch milliseconds to years since 1970.
     *
     * @param epochMillis the epoch milliseconds
     * @return years since 1970 (0 = 1970)
     */
    public static int toYearEpoch(long epochMillis) {
        Instant instant = Instant.ofEpochMilli(epochMillis);
        LocalDate date = LocalDate.ofInstant(instant, ZoneOffset.UTC);
        return date.getYear() - EPOCH_YEAR;
    }

    /**
     * Converts epoch milliseconds to a compact integer based on granularity.
     *
     * @param epochMillis the epoch milliseconds
     * @param granularity the granularity (DAY, MONTH, or YEAR)
     * @return the compact integer representation
     * @throws IllegalArgumentException if granularity is unknown
     */
    public static int toEpochValue(long epochMillis, String granularity) {
        return switch (granularity.toUpperCase()) {
            case "DAY" -> toDayEpoch(epochMillis);
            case "MONTH" -> toMonthEpoch(epochMillis);
            case "YEAR" -> toYearEpoch(epochMillis);
            default -> throw new IllegalArgumentException("Unknown granularity: " + granularity);
        };
    }

    /**
     * Converts days since Unix epoch to epoch milliseconds.
     *
     * @param dayEpoch days since 1970-01-01
     * @return epoch milliseconds at start of day (UTC)
     */
    public static long dayEpochToMillis(int dayEpoch) {
        return (long) dayEpoch * 24 * 60 * 60 * 1000;
    }

    /**
     * Converts months since January 1970 to epoch milliseconds.
     *
     * @param monthEpoch months since January 1970
     * @return epoch milliseconds at start of month (UTC)
     */
    public static long monthEpochToMillis(int monthEpoch) {
        int year = EPOCH_YEAR + monthEpoch / 12;
        int month = (monthEpoch % 12) + 1;
        return LocalDate.of(year, month, 1)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli();
    }

    /**
     * Converts years since 1970 to epoch milliseconds.
     *
     * @param yearEpoch years since 1970
     * @return epoch milliseconds at start of year (UTC)
     */
    public static long yearEpochToMillis(int yearEpoch) {
        return LocalDate.of(EPOCH_YEAR + yearEpoch, 1, 1)
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli();
    }

    /**
     * Converts a compact integer value back to epoch milliseconds based on granularity.
     *
     * @param value the compact integer representation
     * @param granularity the granularity (DAY, MONTH, or YEAR)
     * @return epoch milliseconds
     * @throws IllegalArgumentException if granularity is unknown
     */
    public static long epochValueToMillis(int value, String granularity) {
        return switch (granularity.toUpperCase()) {
            case "DAY" -> dayEpochToMillis(value);
            case "MONTH" -> monthEpochToMillis(value);
            case "YEAR" -> yearEpochToMillis(value);
            default -> throw new IllegalArgumentException("Unknown granularity: " + granularity);
        };
    }
}
