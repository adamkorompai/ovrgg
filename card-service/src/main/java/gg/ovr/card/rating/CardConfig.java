package gg.ovr.card.rating;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

import java.time.Clock;

@Configuration
class CardConfig {

    /** Provisional until rank/role baselines exist (roadmap step 4). */
    @Bean
    CardCalculator cardCalculator() {
        return new CardCalculator();
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * Retries a failed message with an exponential backoff (1 s, 2 s, 4 s… capped at 30 s) for up to
     * 10 minutes, so a short outage (Postgres, Redis, broker) loses nothing. Then logs and skips it:
     * no dead letter topic yet. The partition is paused between attempts, so no rebalance is triggered.
     * Unreadable messages (deserialization, conversion) are never retried.
     */
    @Bean
    DefaultErrorHandler kafkaErrorHandler() {
        ExponentialBackOff backOff = new ExponentialBackOff(1_000L, 2.0);
        backOff.setMaxInterval(30_000L);
        backOff.setMaxElapsedTime(600_000L);
        return new DefaultErrorHandler(backOff);
    }
}
