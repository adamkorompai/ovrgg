package gg.ovr.ingestion.match;

import gg.ovr.common.kafka.Topics;
import gg.ovr.common.match.RiotMatch;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Publishes to {@code match.raw}, keyed by matchId. Blocks until the broker acknowledges,
 * because the caller marks the match processed right after.
 */
@Component
class KafkaMatchPublisher implements MatchPublisher {

    private static final long ACK_TIMEOUT_SECONDS = 10;

    private final KafkaTemplate<String, RiotMatch> kafka;

    KafkaMatchPublisher(KafkaTemplate<String, RiotMatch> kafka) {
        this.kafka = kafka;
    }

    @Override
    public void publish(RiotMatch match) {
        String matchId = match.metadata().matchId();
        try {
            kafka.send(Topics.MATCH_RAW, matchId, match).get(ACK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while publishing match " + matchId, e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException("Could not publish match " + matchId, e);
        }
    }
}
