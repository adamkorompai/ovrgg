package gg.ovr.card.rating;

import gg.ovr.card.PostgresTestConfig;
import gg.ovr.card.performance.PerformanceRepository;
import gg.ovr.card.player.RequestedPlayerRepository;
import gg.ovr.common.card.CardUpdated;
import gg.ovr.common.match.MatchPerformance;
import gg.ovr.common.player.PlayerLookupRequested;
import gg.ovr.common.rating.Attributes;
import org.junit.jupiter.api.AfterEach;
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

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Lookups and performances are consumed by two listeners, on two threads. Here the service's own
 * transactions really commit (no test transaction), so the two threads see each other's writes.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({PostgresTestConfig.class, PerformanceRepository.class, RequestedPlayerRepository.class,
        CardConfig.class, CardService.class, CardServiceConcurrencyTest.Publisher.class})
class CardServiceConcurrencyTest {

    private static final String PUUID = "puuid-concurrency";

    @Autowired
    private CardService service;

    @Autowired
    private PerformanceRepository performances;

    @Autowired
    private BlockingPublisher publisher;

    @Autowired
    private JdbcClient jdbc;

    @AfterEach
    void cleanUp() {
        jdbc.sql("DELETE FROM match_performance WHERE puuid = ?").param(PUUID).update();
        jdbc.sql("DELETE FROM requested_player WHERE puuid = ?").param(PUUID).update();
    }

    @Test
    void performanceWaitsForAConcurrentLookupOfTheSamePlayer() throws Exception {
        performances.save(perf("EUW1_1"));
        ExecutorService threads = Executors.newFixedThreadPool(2);
        try {
            Future<?> lookup = threads.submit(() -> service.onLookup(
                    new PlayerLookupRequested(PUUID, "Alice", "EUW", Instant.now())));
            assertThat(publisher.publishing.await(10, TimeUnit.SECONDS)).isTrue();

            // The lookup is now publishing a card built from one match.
            Future<?> performance = threads.submit(() -> service.onPerformance(perf("EUW1_2")));
            await().during(Duration.ofMillis(500)).atMost(Duration.ofSeconds(2)).until(() -> !performance.isDone());

            publisher.release.countDown();
            lookup.get(10, TimeUnit.SECONDS);
            performance.get(10, TimeUnit.SECONDS);
        } finally {
            threads.shutdownNow();
        }

        // The last card on the topic is the up-to-date one.
        assertThat(publisher.cards).extracting(CardUpdated::matchesCounted).containsExactly(1, 2);
    }

    private static MatchPerformance perf(String matchId) {
        return new MatchPerformance(matchId, PUUID, 420, 1_767_225_600_000L, 1800, "MIDDLE", true,
                3, 2, 7, new Attributes(50, 50, 50, 50, 50, 50), 50, 6.5);
    }

    /** Blocks the first publication until released. */
    static final class BlockingPublisher implements CardPublisher {
        private final List<CardUpdated> cards = new CopyOnWriteArrayList<>();
        private final CountDownLatch publishing = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private final AtomicBoolean first = new AtomicBoolean(true);

        @Override
        public void publish(CardUpdated card) {
            if (first.compareAndSet(true, false)) {
                publishing.countDown();
                try {
                    release.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            cards.add(card);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Publisher {
        @Bean
        BlockingPublisher blockingPublisher() {
            return new BlockingPublisher();
        }
    }
}
