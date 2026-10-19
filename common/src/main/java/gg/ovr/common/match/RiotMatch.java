package gg.ovr.common.match;

/**
 * Subset of a MATCH-V5 match ({@code /lol/match/v5/matches/{matchId}}).
 * Field names mirror the Riot JSON so it can be deserialized as-is.
 */
public record RiotMatch(MatchMetadata metadata, MatchInfo info) {
}
