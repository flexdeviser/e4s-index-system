package com.e4s.index.model;

/**
 * Enumeration of supported time granularities for the index system.
 * 
 * <p>Each granularity level determines how timestamps are aggregated
 * and stored in the index:</p>
 * 
 * <ul>
 *   <li>{@link #DAY} - Tracks data at daily resolution</li>
 *   <li>{@link #MONTH} - Tracks data at monthly resolution</li>
 *   <li>{@link #YEAR} - Tracks data at yearly resolution</li>
 * </ul>
 * 
 * @author E4S Team
 * @version 1.0.0
 */
public enum Granularity {
    
    /**
     * Day-level granularity.
     * Timestamps are converted to days since Unix epoch (1970-01-01).
     * Suitable for tracking daily data availability.
     */
    DAY,
    
    /**
     * Month-level granularity.
     * Timestamps are converted to months since January 1970.
     * Suitable for tracking monthly data availability.
     */
    MONTH,
    
    /**
     * Year-level granularity.
     * Timestamps are converted to years since 1970.
     * Suitable for tracking yearly data availability.
     */
    YEAR
}
