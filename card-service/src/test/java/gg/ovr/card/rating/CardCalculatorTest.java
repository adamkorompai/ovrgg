package gg.ovr.card.rating;

import gg.ovr.card.player.RequestedPlayer;
import gg.ovr.common.card.CardUpdated;
import gg.ovr.common.match.MatchPerformance;
import gg.ovr.common.rating.Attributes;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class CardCalculatorTest {

    private static final RequestedPlayer PLAYER = new RequestedPlayer("puuid-1", "Alice", "EUW");
    private static final Instant NOW = Instant.parse("2026-10-19T12:00:00Z");
    private static final int SOLO_DUO = 420;
    private static final int FLEX = 440;
    private static final int ARAM = 450;
    private static final Attributes FIFTY = new Attributes(50, 50, 50, 50, 50, 50);

    private final CardCalculator calculator = new CardCalculator();

    @Test
    void noCardWithoutEligibleMatch() {
        List<MatchPerformance> performances = List.of(perf(1, ARAM, 1800, 50), perf(2, SOLO_DUO, 200, 50));

        assertThat(calculator.compute(PLAYER, performances, NOW)).isEmpty();
    }

    @Test
    void countsOnlyRankedMatchesOfAtLeastFiveMinutes() {
        List<MatchPerformance> performances = List.of(
                perf(1, SOLO_DUO, 1800, 50),
                perf(2, FLEX, 300, 50),
                perf(3, ARAM, 1800, 50),
                perf(4, SOLO_DUO, 299, 50));

        CardUpdated card = calculator.compute(PLAYER, performances, NOW).orElseThrow();

        assertThat(card.matchesCounted()).isEqualTo(2);
    }

    @Test
    void keepsThe30MostRecentMatches() {
        List<MatchPerformance> performances = new ArrayList<>();
        // 30 recent matches at 80, then 10 older ones at 0 that must be ignored.
        IntStream.rangeClosed(11, 40).forEach(i -> performances.add(perf(i, SOLO_DUO, 1800, 80)));
        IntStream.rangeClosed(1, 10).forEach(i -> performances.add(perf(i, SOLO_DUO, 1800, 0)));

        CardUpdated card = calculator.compute(PLAYER, performances, NOW).orElseThrow();

        assertThat(card.matchesCounted()).isEqualTo(30);
        assertThat(card.ovr()).isEqualTo(80);
    }

    @Test
    void ovrIsTheRoundedAverageMatchScore() {
        List<MatchPerformance> performances = List.of(
                perf(1, SOLO_DUO, 1800, 60),
                perf(2, SOLO_DUO, 1800, 71));

        assertThat(calculator.compute(PLAYER, performances, NOW).orElseThrow().ovr()).isEqualTo(66); // 65.5
    }

    @Test
    void ovrIsClampedBetween1And99() {
        assertThat(calculator.compute(PLAYER, List.of(perf(1, SOLO_DUO, 1800, 0)), NOW).orElseThrow().ovr())
                .isEqualTo(1);
        assertThat(calculator.compute(PLAYER, List.of(perf(1, SOLO_DUO, 1800, 100)), NOW).orElseThrow().ovr())
                .isEqualTo(99);
    }

    @Test
    void attributesAreRoundedAverages() {
        List<MatchPerformance> performances = List.of(
                perf(1, SOLO_DUO, 1800, new Attributes(10, 20, 30, 40, 50, 60)),
                perf(2, SOLO_DUO, 1800, new Attributes(11, 20, 31.2, 40, 50, 61)));

        Attributes attributes = calculator.compute(PLAYER, performances, NOW).orElseThrow().attributes();

        assertThat(attributes).isEqualTo(new Attributes(11, 20, 31, 40, 50, 61)); // 10.5 → 11, 30.6 → 31, 60.5 → 61
    }

    @Test
    void provisionalBelowTenMatches() {
        List<MatchPerformance> nine = IntStream.rangeClosed(1, 9).mapToObj(i -> perf(i, SOLO_DUO, 1800, 50)).toList();
        List<MatchPerformance> ten = IntStream.rangeClosed(1, 10).mapToObj(i -> perf(i, SOLO_DUO, 1800, 50)).toList();

        assertThat(calculator.compute(PLAYER, nine, NOW).orElseThrow().provisional()).isTrue();
        assertThat(calculator.compute(PLAYER, ten, NOW).orElseThrow().provisional()).isFalse();
    }

    @Test
    void recentRatingsAreTheFiveLatestMostRecentFirst() {
        // Given out of order on purpose.
        List<MatchPerformance> performances = List.of(
                perf(3, SOLO_DUO, 1800, 30),
                perf(7, SOLO_DUO, 1800, 70),
                perf(1, SOLO_DUO, 1800, 10),
                perf(5, SOLO_DUO, 1800, 50),
                perf(6, SOLO_DUO, 1800, 60),
                perf(2, SOLO_DUO, 1800, 20),
                perf(4, SOLO_DUO, 1800, 40));

        CardUpdated card = calculator.compute(PLAYER, performances, NOW).orElseThrow();

        // rating = 3 + 7 × score / 100
        assertThat(card.recentRatings()).containsExactly(7.9, 7.2, 6.5, 5.8, 5.1);
    }

    @Test
    void copiesPlayerIdentityAndTimestamp() {
        CardUpdated card = calculator.compute(PLAYER, List.of(perf(1, SOLO_DUO, 1800, 50)), NOW).orElseThrow();

        assertThat(card.puuid()).isEqualTo("puuid-1");
        assertThat(card.gameName()).isEqualTo("Alice");
        assertThat(card.tagLine()).isEqualTo("EUW");
        assertThat(card.updatedAt()).isEqualTo(NOW);
    }

    /** {@code order} sets gameCreation: higher is more recent. */
    private static MatchPerformance perf(int order, int queueId, long durationSeconds, double score) {
        return perf(order, queueId, durationSeconds, score, FIFTY);
    }

    private static MatchPerformance perf(int order, int queueId, long durationSeconds, Attributes attributes) {
        return perf(order, queueId, durationSeconds, 50, attributes);
    }

    private static MatchPerformance perf(int order, int queueId, long durationSeconds, double score,
                                         Attributes attributes) {
        double rating = Math.round((3.0 + 7.0 * score / 100) * 10) / 10.0;
        return new MatchPerformance("EUW1_" + order, "puuid-1", queueId, 1_000_000L * order, durationSeconds,
                "MIDDLE", true, 1, 1, 1, attributes, score, rating);
    }
}
