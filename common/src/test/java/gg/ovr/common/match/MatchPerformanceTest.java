package gg.ovr.common.match;

import gg.ovr.common.rating.Attributes;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class MatchPerformanceTest {

    private static final Attributes ATTRIBUTES = new Attributes(50, 50, 50, 50, 50, 50);

    @Test
    void requiresMatchId() {
        assertThatNullPointerException()
                .isThrownBy(() -> new MatchPerformance(null, "puuid-1", 420, 0L, 1800, "TOP", true,
                        1, 2, 3, ATTRIBUTES, 50.0, 6.5))
                .withMessageContaining("matchId");
    }

    @Test
    void requiresPuuid() {
        assertThatNullPointerException()
                .isThrownBy(() -> new MatchPerformance("EUW1_1", null, 420, 0L, 1800, "TOP", true,
                        1, 2, 3, ATTRIBUTES, 50.0, 6.5))
                .withMessageContaining("puuid");
    }
}
