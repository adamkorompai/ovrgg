package gg.ovr.card.rating;

import gg.ovr.common.kafka.Topics;
import gg.ovr.common.match.MatchPerformance;
import gg.ovr.common.player.PlayerLookupRequested;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * One consumer group per topic: a restart of one listener does not rebalance the other,
 * and each lag shows up on its own in Kafka UI.
 */
@Component
class CardListeners {

    private final CardService cardService;

    CardListeners(CardService cardService) {
        this.cardService = cardService;
    }

    @KafkaListener(topics = Topics.PLAYER_LOOKUP_REQUESTED, groupId = "card-service-lookups",
            properties = "spring.json.value.default.type=gg.ovr.common.player.PlayerLookupRequested")
    void onLookupRequested(PlayerLookupRequested lookup) {
        cardService.onLookup(lookup);
    }

    @KafkaListener(topics = Topics.MATCH_PERFORMANCE, groupId = "card-service-performances",
            properties = "spring.json.value.default.type=gg.ovr.common.match.MatchPerformance")
    void onPerformance(MatchPerformance performance) {
        cardService.onPerformance(performance);
    }
}
