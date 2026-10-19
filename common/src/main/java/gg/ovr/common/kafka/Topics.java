package gg.ovr.common.kafka;

/**
 * Kafka topic names, created by {@code kafka-init} in infra/docker-compose.yml (auto-creation is off).
 */
public final class Topics {

    /** Key: puuid. Value: {@link gg.ovr.common.player.PlayerLookupRequested}. */
    public static final String PLAYER_LOOKUP_REQUESTED = "player.lookup-requested";
    /** Key: matchId. Value: {@link gg.ovr.common.match.RiotMatch}. */
    public static final String MATCH_RAW = "match.raw";
    /** Key: puuid. Value: {@link gg.ovr.common.match.MatchPerformance}. */
    public static final String MATCH_PERFORMANCE = "match.performance";
    /** Key: puuid, compacted. Value: {@link gg.ovr.common.card.CardUpdated}. */
    public static final String CARD_UPDATED = "card.updated";

    private Topics() {
    }
}
