package gg.ovr.ingestion.match;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Picks the {@link MatchSource} from {@code ovr.match-source}. Only "fake" exists for now;
 * the Riot implementation arrives in roadmap step 3.
 */
@Configuration
class MatchSourceConfig {

    @Bean
    @ConditionalOnProperty(name = "ovr.match-source", havingValue = "fake", matchIfMissing = true)
    MatchSource fakeMatchSource() {
        return new FakeMatchSource();
    }
}
