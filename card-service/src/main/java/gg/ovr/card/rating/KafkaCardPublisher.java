package gg.ovr.card.rating;

import gg.ovr.common.card.CardUpdated;
import gg.ovr.common.kafka.Topics;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Publishes to {@code card.updated}, keyed by puuid. The topic is compacted: the key is mandatory,
 * and only the latest card of each player is eventually kept.
 */
@Component
class KafkaCardPublisher implements CardPublisher {

    private static final long ACK_TIMEOUT_SECONDS = 10;

    private final KafkaTemplate<String, CardUpdated> kafka;

    KafkaCardPublisher(KafkaTemplate<String, CardUpdated> kafka) {
        this.kafka = kafka;
    }

    @Override
    public void publish(CardUpdated card) {
        try {
            kafka.send(Topics.CARD_UPDATED, card.puuid(), card).get(ACK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while publishing card of " + card.puuid(), e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("Could not publish card of " + card.puuid(), e);
        }
    }
}
