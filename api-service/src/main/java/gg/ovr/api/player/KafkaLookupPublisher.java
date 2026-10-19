package gg.ovr.api.player;

import gg.ovr.common.kafka.Topics;
import gg.ovr.common.player.PlayerLookupRequested;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Publishes to {@code player.lookup-requested}, keyed by puuid. Blocks until the broker acknowledges,
 * so the 202 returned to the client means the lookup will be processed.
 */
@Component
class KafkaLookupPublisher implements LookupPublisher {

    private static final long ACK_TIMEOUT_SECONDS = 10;

    private final KafkaTemplate<String, PlayerLookupRequested> kafka;

    KafkaLookupPublisher(KafkaTemplate<String, PlayerLookupRequested> kafka) {
        this.kafka = kafka;
    }

    @Override
    public void publish(PlayerLookupRequested lookup) {
        try {
            kafka.send(Topics.PLAYER_LOOKUP_REQUESTED, lookup.puuid(), lookup).get(ACK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while publishing lookup of " + lookup.puuid(), e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("Could not publish lookup of " + lookup.puuid(), e);
        }
    }
}
