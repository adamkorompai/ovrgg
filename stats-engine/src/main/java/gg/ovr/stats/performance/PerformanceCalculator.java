package gg.ovr.stats.performance;

import gg.ovr.common.match.MatchPerformance;
import gg.ovr.common.match.RiotMatch;

import java.util.List;

/**
 * Rates every participant of a match. Pure function: same match, same performances.
 */
public interface PerformanceCalculator {

    /**
     * @return one performance per participant, in the order of {@code info.participants}
     */
    List<MatchPerformance> compute(RiotMatch match);
}
