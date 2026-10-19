package gg.ovr.ingestion.match;

import gg.ovr.common.match.RiotMatch;

import java.util.List;
import java.util.Optional;

/**
 * Where matches come from: the Riot API in production, generated data in dev and tests.
 */
public interface MatchSource {

    /**
     * @return up to {@code count} match ids of the player, most recent first
     */
    List<String> findRecentMatchIds(String puuid, int count);

    Optional<RiotMatch> findMatch(String matchId);
}
