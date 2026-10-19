package gg.ovr.stats.performance;

import gg.ovr.common.match.Challenges;
import gg.ovr.common.match.MatchInfo;
import gg.ovr.common.match.MatchPerformance;
import gg.ovr.common.match.Participant;
import gg.ovr.common.match.RiotMatch;
import gg.ovr.common.rating.Attributes;

import java.util.List;
import java.util.function.Function;
import java.util.stream.DoubleStream;

/**
 * Stand-in rating until rank/role baselines exist: one metric per attribute, scaled between
 * fixed bounds. See SCORING.md, "Annexe — Formule provisoire (étape 2)". Replaced in roadmap step 4.
 */
public class ProvisionalPerformanceCalculator implements PerformanceCalculator {

    private static final double NEUTRAL = 50.0;

    @Override
    public List<MatchPerformance> compute(RiotMatch match) {
        MatchInfo info = match.info();
        double minutes = Math.max(1.0, info.gameDuration() / 60.0);
        return info.participants().stream()
                .map(participant -> toPerformance(match.metadata().matchId(), info, participant, minutes))
                .toList();
    }

    private static MatchPerformance toPerformance(String matchId, MatchInfo info, Participant p, double minutes) {
        Attributes attributes = attributes(p, minutes);
        double matchScore = DoubleStream.of(attributes.agr(), attributes.frm(), attributes.vis(),
                attributes.tea(), attributes.sur(), attributes.imp()).average().orElseThrow();
        double matchRating = Math.round((3.0 + 7.0 * matchScore / 100) * 10) / 10.0;
        return new MatchPerformance(matchId, p.puuid(), info.queueId(), info.gameCreation(), info.gameDuration(),
                p.teamPosition(), p.win(), p.kills(), p.deaths(), p.assists(), attributes, matchScore, matchRating);
    }

    private static Attributes attributes(Participant p, double minutes) {
        return new Attributes(
                challenge(p, Challenges::teamDamagePercentage, 0.0, 0.4),
                scale((p.totalMinionsKilled() + p.neutralMinionsKilled()) / minutes, 0, 10),
                scale(p.visionScore() / minutes, 0, 3),
                challenge(p, Challenges::killParticipation, 0.0, 1.0),
                scale(p.deaths() / minutes, 0.5, 0),
                scale(p.damageDealtToObjectives() / minutes, 0, 1000));
    }

    /** Riot omits some challenges: absent is neutral, not 0. */
    private static double challenge(Participant p, Function<Challenges, Double> metric, double low, double high) {
        Double value = p.challenges() == null ? null : metric.apply(p.challenges());
        return value == null ? NEUTRAL : scale(value, low, high);
    }

    /** Linear 0–100 between {@code low} and {@code high}; {@code low > high} inverts the scale. */
    private static double scale(double value, double low, double high) {
        return Math.clamp(100 * (value - low) / (high - low), 0.0, 100.0);
    }
}
