package gg.ovr.ingestion.lookup;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * @param matchCount   recent matches fetched per lookup (SCORING.md §7 window)
 * @param processedTtl how long a published match id is remembered
 */
@ConfigurationProperties("ovr.ingestion")
public record IngestionProperties(int matchCount, Duration processedTtl) {
}
