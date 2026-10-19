package gg.ovr.api.card;

import gg.ovr.common.card.CardUpdated;
import gg.ovr.common.kafka.Topics;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Every instance needs every card (its own memory, its own SSE clients), so each one gets a fresh
 * consumer group: no committed offset, so it replays the compacted topic from the start, which
 * rebuilds the {@link CardStore}. Abandoned groups expire on the broker (offsets.retention.minutes).
 */
@Component
class CardUpdatedListener {

    private final CardFeed feed;

    CardUpdatedListener(CardFeed feed) {
        this.feed = feed;
    }

    @KafkaListener(topics = Topics.CARD_UPDATED, groupId = "api-service-#{T(java.util.UUID).randomUUID()}")
    void onCardUpdated(CardUpdated card) {
        feed.onCardUpdated(card);
    }
}
