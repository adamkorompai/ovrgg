package gg.ovr.stats.performance;

import gg.ovr.common.match.MatchInfo;
import gg.ovr.common.match.MatchMetadata;
import gg.ovr.common.match.MatchPerformance;
import gg.ovr.common.match.Participant;
import gg.ovr.common.match.RiotMatch;
import gg.ovr.common.rating.Attributes;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class ProvisionalPerformanceCalculatorTest {

    private static final long FORTY_MINUTES = 2400;

    private final PerformanceCalculator calculator = new ProvisionalPerformanceCalculator();

    @Test
    void midpointOfEveryBoundGives50() {
        MatchPerformance performance = single(FORTY_MINUTES, p -> { });

        assertThat(performance.attributes()).isEqualTo(new Attributes(50, 50, 50, 50, 50, 50));
        assertThat(performance.matchScore()).isEqualTo(50.0);
        assertThat(performance.matchRating()).isEqualTo(6.5);
    }

    @Test
    void clampsAboveUpperBoundTo100() {
        MatchPerformance performance = single(FORTY_MINUTES, p -> {
            p.totalMinionsKilled = 600; // 15 cs/min
            p.teamDamagePercentage = 0.6;
        });

        assertThat(performance.attributes().frm()).isEqualTo(100);
        assertThat(performance.attributes().agr()).isEqualTo(100);
    }

    @Test
    void survivalIsInvertedAndClampedAt0() {
        Attributes deathless = single(FORTY_MINUTES, p -> p.deaths = 0).attributes();
        Attributes feeding = single(FORTY_MINUTES, p -> p.deaths = 40).attributes(); // 1 death/min

        assertThat(deathless.sur()).isEqualTo(100);
        assertThat(feeding.sur()).isZero();
    }

    @Test
    void missingChallengeMetricIsNeutral() {
        Attributes attributes = single(FORTY_MINUTES, p -> {
            p.teamDamagePercentage = null;
            p.killParticipation = null;
        }).attributes();

        assertThat(attributes.agr()).isEqualTo(50);
        assertThat(attributes.tea()).isEqualTo(50);
    }

    @Test
    void missingChallengesObjectIsNeutral() {
        Attributes attributes = single(FORTY_MINUTES, p -> {
            p.withChallenges = false;
            p.teamDamagePercentage = 0.4;
        }).attributes();

        assertThat(attributes.agr()).isEqualTo(50);
        assertThat(attributes.tea()).isEqualTo(50);
    }

    @Test
    void durationUnderOneMinuteCountsAsOneMinute() {
        MatchPerformance performance = single(30, p -> {
            p.totalMinionsKilled = 5;
            p.neutralMinionsKilled = 0;
        });

        assertThat(performance.attributes().frm()).isEqualTo(50);
    }

    @Test
    void ratingIsRoundedToOneDecimal() {
        // AGR 100, the rest 50: score 58.33, rating 3 + 7 × 0.5833 = 7.083
        MatchPerformance performance = single(FORTY_MINUTES, p -> p.teamDamagePercentage = 0.4);

        assertThat(performance.matchScore()).isCloseTo(58.333, within(0.001));
        assertThat(performance.matchRating()).isEqualTo(7.1);
    }

    @Test
    void ratingRangeIs3To10() {
        MatchPerformance best = single(FORTY_MINUTES, p -> {
            p.teamDamagePercentage = 1.0;
            p.totalMinionsKilled = 600;
            p.visionScore = 200;
            p.killParticipation = 1.0;
            p.deaths = 0;
            p.damageDealtToObjectives = 60_000;
        });
        MatchPerformance worst = single(FORTY_MINUTES, p -> {
            p.teamDamagePercentage = 0.0;
            p.totalMinionsKilled = 0;
            p.neutralMinionsKilled = 0;
            p.visionScore = 0;
            p.killParticipation = 0.0;
            p.deaths = 40;
            p.damageDealtToObjectives = 0;
        });

        assertThat(best.matchRating()).isEqualTo(10.0);
        assertThat(worst.matchRating()).isEqualTo(3.0);
    }

    @Test
    void returnsOnePerformancePerParticipantWithMatchContext() {
        Participant top = participant(p -> {
            p.puuid = "puuid-top";
            p.teamPosition = "TOP";
            p.win = false;
            p.kills = 7;
            p.deaths = 2;
            p.assists = 9;
        });
        Participant aram = participant(p -> {
            p.puuid = "puuid-aram";
            p.teamPosition = "";
        });
        RiotMatch match = match(FORTY_MINUTES, List.of(top, aram));

        List<MatchPerformance> performances = calculator.compute(match);

        assertThat(performances).extracting(MatchPerformance::puuid).containsExactly("puuid-top", "puuid-aram");
        MatchPerformance first = performances.getFirst();
        assertThat(first.matchId()).isEqualTo("EUW1_42");
        assertThat(first.queueId()).isEqualTo(420);
        assertThat(first.gameCreation()).isEqualTo(1_767_225_600_000L);
        assertThat(first.durationSeconds()).isEqualTo(FORTY_MINUTES);
        assertThat(first.teamPosition()).isEqualTo("TOP");
        assertThat(first.win()).isFalse();
        assertThat(first.kills()).isEqualTo(7);
        assertThat(first.deaths()).isEqualTo(2);
        assertThat(first.assists()).isEqualTo(9);
        assertThat(performances.get(1).teamPosition()).isEmpty();
    }

    private MatchPerformance single(long durationSeconds, Consumer<TestParticipant> customizer) {
        List<MatchPerformance> performances = calculator.compute(match(durationSeconds, List.of(participant(customizer))));
        assertThat(performances).hasSize(1);
        return performances.getFirst();
    }

    private static Participant participant(Consumer<TestParticipant> customizer) {
        TestParticipant participant = new TestParticipant();
        customizer.accept(participant);
        return participant.build();
    }

    private static RiotMatch match(long durationSeconds, List<Participant> participants) {
        MatchInfo info = new MatchInfo(42, 1_767_225_600_000L, durationSeconds, 420, "CLASSIC", "EUW1", participants);
        return new RiotMatch(new MatchMetadata("EUW1_42", participants.stream().map(Participant::puuid).toList()), info);
    }
}
