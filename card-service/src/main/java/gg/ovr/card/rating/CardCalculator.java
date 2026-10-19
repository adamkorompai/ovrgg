package gg.ovr.card.rating;

import gg.ovr.card.player.RequestedPlayer;
import gg.ovr.common.card.CardUpdated;
import gg.ovr.common.match.MatchPerformance;
import gg.ovr.common.match.Queue;
import gg.ovr.common.rating.Attributes;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.ToDoubleFunction;

/**
 * Aggregates a player's performances into a card. See SCORING.md, "Annexe — Formule provisoire (étape 2)":
 * plain averages, no recency weighting nor shrinkage yet (roadmap step 4).
 */
public class CardCalculator {

    static final int WINDOW = 30;
    static final int MIN_DURATION_SECONDS = 300;
    static final int OFFICIAL_CARD_MIN_MATCHES = 10;
    static final int RECENT_RATINGS = 5;

    private static final Comparator<MatchPerformance> MOST_RECENT_FIRST = Comparator
            .comparingLong(MatchPerformance::gameCreation).reversed()
            .thenComparing(MatchPerformance::matchId);

    /**
     * @return empty if no ranked match of at least five minutes is known yet
     */
    public Optional<CardUpdated> compute(RequestedPlayer player, List<MatchPerformance> performances, Instant now) {
        List<MatchPerformance> counted = performances.stream()
                .filter(p -> Queue.isRankedQueueId(p.queueId()))
                .filter(p -> p.durationSeconds() >= MIN_DURATION_SECONDS)
                .sorted(MOST_RECENT_FIRST)
                .limit(WINDOW)
                .toList();
        if (counted.isEmpty()) {
            return Optional.empty();
        }
        int ovr = (int) Math.round(Math.clamp(average(counted, MatchPerformance::matchScore), 1.0, 99.0));
        return Optional.of(new CardUpdated(
                player.puuid(),
                player.gameName(),
                player.tagLine(),
                ovr,
                averageAttributes(counted),
                counted.size(),
                counted.size() < OFFICIAL_CARD_MIN_MATCHES,
                counted.stream().limit(RECENT_RATINGS).map(MatchPerformance::matchRating).toList(),
                now));
    }

    private static Attributes averageAttributes(List<MatchPerformance> counted) {
        return new Attributes(
                roundedAverage(counted, p -> p.attributes().agr()),
                roundedAverage(counted, p -> p.attributes().frm()),
                roundedAverage(counted, p -> p.attributes().vis()),
                roundedAverage(counted, p -> p.attributes().tea()),
                roundedAverage(counted, p -> p.attributes().sur()),
                roundedAverage(counted, p -> p.attributes().imp()));
    }

    private static double roundedAverage(List<MatchPerformance> counted, ToDoubleFunction<MatchPerformance> metric) {
        return Math.round(average(counted, metric));
    }

    private static double average(List<MatchPerformance> counted, ToDoubleFunction<MatchPerformance> metric) {
        return counted.stream().mapToDouble(metric).average().orElseThrow();
    }
}
