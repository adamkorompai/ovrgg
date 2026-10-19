package gg.ovr.api.card;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
class CardConfig {

    @Bean
    CardStore cardStore() {
        return new InMemoryCardStore();
    }

    @Bean
    CardStreamRegistry cardStreamRegistry(@Value("${ovr.api.card-stream-timeout}") Duration timeout) {
        return new CardStreamRegistry(timeout);
    }

    @Bean
    CardFeed cardFeed(CardStore store, CardStreamRegistry streams) {
        return new CardFeed(store, streams);
    }
}
