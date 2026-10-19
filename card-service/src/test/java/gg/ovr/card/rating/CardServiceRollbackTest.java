package gg.ovr.card.rating;

import gg.ovr.card.PostgresTestConfig;
import gg.ovr.card.performance.PerformanceRepository;
import gg.ovr.card.player.RequestedPlayerRepository;
import gg.ovr.common.card.CardUpdated;
import gg.ovr.common.match.MatchPerformance;
import gg.ovr.common.player.PlayerLookupRequested;
import gg.ovr.common.rating.Attributes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The card is published before the transaction commits. If the commit then fails, the card is
 * already on the topic: these tests check that the redelivered message repairs the state.
 * A failure thrown right after the send rolls back exactly like a failed commit would.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({PostgresTestConfig.class, PerformanceRepository.class, RequestedPlayerRepository.class,
        CardConfig.class, CardService.class, CardServiceRollbackTest.Publisher.class})
class CardServiceRollbackTest {

    private static final String PUUID = "puuid-rollback";
    private static final PlayerLookupRequested LOOKUP =
            new PlayerLookupRequested(PUUID, "Alice", "EUW", Instant.parse("2026-10-19T12:00:00Z"));

    @Autowired
    private CardService service;

    @Autowired
    private PerformanceRepository performances;

    @Autowired
    private RequestedPlayerRepository requestedPlayers;

    @Autowired
    private FailingAfterSendPublisher publisher;

    @Autowired
    private JdbcClient jdbc;

    @BeforeEach
    void setUp() {
        publisher.cards.clear();
        publisher.failNextAfterSend = false;
    }

    @AfterEach
    void cleanUp() {
        jdbc.sql("DELETE FROM match_performance WHERE puuid = ?").param(PUUID).update();
        jdbc.sql("DELETE FROM requested_player WHERE puuid = ?").param(PUUID).update();
    }

    @Test
    void redeliveredPerformanceRestoresWhatTheSentCardAnnounced() {
        service.onLookup(LOOKUP);
        publisher.failNextAfterSend = true;

        assertThatThrownBy(() -> service.onPerformance(perf("EUW1_1"))).hasMessageContaining("commit failed");
        // The card counting EUW1_1 is out, but EUW1_1 itself was rolled back.
        assertThat(publisher.cards).extracting(CardUpdated::matchesCounted).containsExactly(1);
        assertThat(performances.findByPuuid(PUUID)).isEmpty();

        // The listener threw, so the offset was not committed: Kafka delivers the same message again.
        service.onPerformance(perf("EUW1_1"));

        assertThat(performances.findByPuuid(PUUID)).hasSize(1);
        assertThat(publisher.cards).extracting(CardUpdated::matchesCounted).containsExactly(1, 1);
    }

    @Test
    void redeliveredLookupRestoresTheRequestedPlayer() {
        performances.save(perf("EUW1_1"));
        publisher.failNextAfterSend = true;

        assertThatThrownBy(() -> service.onLookup(LOOKUP)).hasMessageContaining("commit failed");
        assertThat(requestedPlayers.findByPuuid(PUUID)).isEmpty();

        service.onLookup(LOOKUP);

        assertThat(requestedPlayers.findByPuuid(PUUID)).isPresent();
        // Later performances of the player keep updating the card.
        service.onPerformance(perf("EUW1_2"));
        assertThat(publisher.cards).extracting(CardUpdated::matchesCounted).containsExactly(1, 1, 2);
    }

    private static MatchPerformance perf(String matchId) {
        return new MatchPerformance(matchId, PUUID, 420, 1_767_225_600_000L + matchId.hashCode(), 1800, "MIDDLE",
                true, 3, 2, 7, new Attributes(50, 50, 50, 50, 50, 50), 50, 6.5);
    }

    static final class FailingAfterSendPublisher implements CardPublisher {
        private final List<CardUpdated> cards = new ArrayList<>();
        private boolean failNextAfterSend;

        @Override
        public void publish(CardUpdated card) {
            cards.add(card);
            if (failNextAfterSend) {
                failNextAfterSend = false;
                throw new IllegalStateException("commit failed after the card was sent");
            }
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Publisher {
        @Bean
        FailingAfterSendPublisher failingAfterSendPublisher() {
            return new FailingAfterSendPublisher();
        }
    }
}
