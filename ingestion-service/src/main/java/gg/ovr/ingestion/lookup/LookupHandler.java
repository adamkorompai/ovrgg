package gg.ovr.ingestion.lookup;

import gg.ovr.common.match.RiotMatch;
import gg.ovr.common.player.PlayerLookupRequested;
import gg.ovr.ingestion.match.MatchPublisher;
import gg.ovr.ingestion.match.MatchSource;
import gg.ovr.ingestion.match.ProcessedMatches;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Fetches the recent matches of a looked-up player and publishes the ones never seen before.
 * Publish first, mark second: a crash in between means a duplicate later, never a lost match.
 */
public class LookupHandler {

    private static final Logger log = LoggerFactory.getLogger(LookupHandler.class);

    private final MatchSource source;
    private final ProcessedMatches processed;
    private final MatchPublisher publisher;
    private final int matchCount;

    public LookupHandler(MatchSource source, ProcessedMatches processed, MatchPublisher publisher, int matchCount) {
        this.source = source;
        this.processed = processed;
        this.publisher = publisher;
        this.matchCount = matchCount;
    }

    public void handle(PlayerLookupRequested lookup) {
        int published = 0;
        int skipped = 0;
        for (String matchId : source.findRecentMatchIds(lookup.puuid(), matchCount)) {
            if (processed.isProcessed(matchId)) {
                skipped++;
                continue;
            }
            Optional<RiotMatch> match = source.findMatch(matchId);
            if (match.isEmpty()) {
                log.warn("Match {} not found, skipped", matchId);
                continue;
            }
            publisher.publish(match.get());
            processed.markProcessed(matchId);
            published++;
        }
        log.info("Lookup {}: {} matches published, {} already processed", lookup.puuid(), published, skipped);
    }
}
