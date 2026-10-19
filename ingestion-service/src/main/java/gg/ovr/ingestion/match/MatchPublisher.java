package gg.ovr.ingestion.match;

import gg.ovr.common.match.RiotMatch;

/**
 * Sends a match to {@code match.raw}.
 */
public interface MatchPublisher {

    /**
     * Returns only once the broker has acknowledged the match, so the caller can then mark it processed.
     *
     * @throws RuntimeException if the match could not be published
     */
    void publish(RiotMatch match);
}
