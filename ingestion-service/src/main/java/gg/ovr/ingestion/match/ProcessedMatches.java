package gg.ovr.ingestion.match;

/**
 * Match ids already published to {@code match.raw}, so a new lookup does not fetch them again.
 * Shared between instances. Not a hard guarantee: card-service still ignores duplicates.
 */
public interface ProcessedMatches {

    boolean isProcessed(String matchId);

    void markProcessed(String matchId);
}
