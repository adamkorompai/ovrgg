package gg.ovr.common.match;

import gg.ovr.common.rating.Attributes;

import java.util.Objects;

/**
 * Value of {@code match.performance} (key: puuid). One player in one match; a match yields ten.
 *
 * @param gameCreation    epoch millis
 * @param teamPosition    Riot {@code teamPosition}, empty in ARAM
 * @param matchScore      0–100 (SCORING.md §6)
 * @param matchRating     displayed rating out of 10
 */
public record MatchPerformance(
        String matchId,
        String puuid,
        int queueId,
        long gameCreation,
        long durationSeconds,
        String teamPosition,
        boolean win,
        int kills,
        int deaths,
        int assists,
        Attributes attributes,
        double matchScore,
        double matchRating) {

    public MatchPerformance {
        Objects.requireNonNull(matchId, "matchId");
        Objects.requireNonNull(puuid, "puuid");
        Objects.requireNonNull(attributes, "attributes");
    }
}
