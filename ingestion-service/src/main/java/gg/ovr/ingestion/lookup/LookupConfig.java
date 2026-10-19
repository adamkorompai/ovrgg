package gg.ovr.ingestion.lookup;

import gg.ovr.ingestion.match.MatchPublisher;
import gg.ovr.ingestion.match.MatchSource;
import gg.ovr.ingestion.match.ProcessedMatches;
import gg.ovr.ingestion.match.RedisProcessedMatches;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

@Configuration
@EnableConfigurationProperties(IngestionProperties.class)
class LookupConfig {

    @Bean
    ProcessedMatches processedMatches(StringRedisTemplate redis, IngestionProperties properties) {
        return new RedisProcessedMatches(redis, properties.processedTtl());
    }

    @Bean
    LookupHandler lookupHandler(MatchSource source, ProcessedMatches processed, MatchPublisher publisher,
                                IngestionProperties properties) {
        return new LookupHandler(source, processed, publisher, properties.matchCount());
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
