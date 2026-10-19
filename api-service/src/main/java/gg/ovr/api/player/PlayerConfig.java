package gg.ovr.api.player;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
class PlayerConfig {

    /** Only "fake" exists for now; ACCOUNT-V1 arrives in roadmap step 3. */
    @Bean
    @ConditionalOnProperty(name = "ovr.account-source", havingValue = "fake", matchIfMissing = true)
    AccountResolver fakeAccountResolver() {
        return new FakeAccountResolver();
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    LookupService lookupService(AccountResolver accounts, LookupPublisher publisher, Clock clock) {
        return new LookupService(accounts, publisher, clock);
    }
}
