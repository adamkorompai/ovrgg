package gg.ovr.card.rating;

import gg.ovr.card.PostgresTestConfig;
import gg.ovr.card.performance.PerformanceRepository;
import gg.ovr.card.player.RequestedPlayerRepository;
import gg.ovr.common.card.CardUpdated;
import gg.ovr.common.match.MatchPerformance;
import gg.ovr.common.player.PlayerLookupRequested;
import gg.ovr.common.rating.Attributes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PostgresTestConfig.class, PerformanceRepository.class, RequestedPlayerRepository.class,
        CardConfig.class, CardService.class, CardServiceTest.Publisher.class})
class CardServiceTest {

    private static final PlayerLookupRequested LOOKUP =
            new PlayerLookupRequested("puuid-1", "Alice", "EUW", Instant.parse("2026-10-19T12:00:00Z"));

    @Autowired
    private CardService service;

    @Autowired
    private RecordingPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher.cards.clear();
    }

    @Test
    void noCardForAPlayerNobodyLookedUp() {
        service.onPerformance(perf("EUW1_1", "puuid-1"));

        assertThat(publisher.cards).isEmpty();
    }

    @Test
    void performanceOfARequestedPlayerPublishesTheCard() {
        service.onLookup(LOOKUP);

        service.onPerformance(perf("EUW1_1", "puuid-1"));

        assertThat(publisher.cards).singleElement().satisfies(card -> {
            assertThat(card.puuid()).isEqualTo("puuid-1");
            assertThat(card.gameName()).isEqualTo("Alice");
            assertThat(card.matchesCounted()).isEqualTo(1);
        });
    }

    @Test
    void lookupAfterPerformancesPublishesTheCardRightAway() {
        service.onPerformance(perf("EUW1_1", "puuid-1"));
        service.onPerformance(perf("EUW1_2", "puuid-1"));

        service.onLookup(LOOKUP);

        assertThat(publisher.cards).singleElement()
                .satisfies(card -> assertThat(card.matchesCounted()).isEqualTo(2));
    }

    @Test
    void lookupWithoutPerformancePublishesNothing() {
        service.onLookup(LOOKUP);

        assertThat(publisher.cards).isEmpty();
    }

    @Test
    void duplicatePerformanceIsCountedOnceButRepublished() {
        service.onLookup(LOOKUP);
        service.onPerformance(perf("EUW1_1", "puuid-1"));

        service.onPerformance(perf("EUW1_1", "puuid-1"));

        assertThat(publisher.cards).hasSize(2)
                .allSatisfy(card -> assertThat(card.matchesCounted()).isEqualTo(1));
    }

    @Test
    void performanceOfAnotherPlayerDoesNotPublishTheRequestedCard() {
        service.onLookup(LOOKUP);

        service.onPerformance(perf("EUW1_1", "puuid-2"));

        assertThat(publisher.cards).isEmpty();
    }

    private static MatchPerformance perf(String matchId, String puuid) {
        return new MatchPerformance(matchId, puuid, 420, 1_767_225_600_000L, 1800, "MIDDLE", true,
                3, 2, 7, new Attributes(50, 50, 50, 50, 50, 50), 50, 6.5);
    }

    static final class RecordingPublisher implements CardPublisher {
        private final List<CardUpdated> cards = new ArrayList<>();

        @Override
        public void publish(CardUpdated card) {
            cards.add(card);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Publisher {
        @Bean
        RecordingPublisher recordingPublisher() {
            return new RecordingPublisher();
        }
    }
}
