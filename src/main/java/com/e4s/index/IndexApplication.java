package com.e4s.index;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main entry point for the E4S Index System application.
 * 
 * <p>This is a Spring Boot application that provides a multi-tenant time-series
 * data index service. It enables fast lookups of data existence across different
 * time granularities (DAY, MONTH, YEAR) without querying the primary database.</p>
 * 
 * <p>The application exposes REST APIs for:</p>
 * <ul>
 *   <li>Creating and managing named indexes</li>
 *   <li>Marking timestamps for entities</li>
 *   <li>Checking existence of data at specific timestamps</li>
 *   <li>Finding previous/next available data points</li>
 * </ul>
 * 
 * @author E4S Team
 * @version 1.0.0
 */
@SpringBootApplication
public class IndexApplication {

    /**
     * Main entry point for the application.
     *
     * @param args command line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(IndexApplication.class, args);
    }
}
