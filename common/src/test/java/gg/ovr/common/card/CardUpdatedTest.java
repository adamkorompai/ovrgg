package gg.ovr.common.card;

import gg.ovr.common.rating.Attributes;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class CardUpdatedTest {

    private static final Attributes ATTRIBUTES = new Attributes(50, 50, 50, 50, 50, 50);
    private static final Instant NOW = Instant.parse("2026-10-19T12:00:00Z");

    @Test
    void copiesRecentRatings() {
        List<Double> ratings = new ArrayList<>(List.of(7.1, 6.4));
        CardUpdated card = card(50, ratings);

        ratings.add(9.9);

        assertThat(card.recentRatings()).containsExactly(7.1, 6.4);
    }

    @Test
    void acceptsOvrBounds() {
        assertThat(card(1, List.of()).ovr()).isEqualTo(1);
        assertThat(card(99, List.of()).ovr()).isEqualTo(99);
    }

    @Test
    void rejectsOvrOutsideBounds() {
        assertThatIllegalArgumentException().isThrownBy(() -> card(0, List.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> card(100, List.of()));
    }

    @Test
    void requiresPuuid() {
        assertThatNullPointerException()
                .isThrownBy(() -> new CardUpdated(null, "Faker", "KR1", 50, ATTRIBUTES, 1, true, List.of(), NOW))
                .withMessageContaining("puuid");
    }

    private static CardUpdated card(int ovr, List<Double> ratings) {
        return new CardUpdated("puuid-1", "Faker", "KR1", ovr, ATTRIBUTES, 12, false, ratings, NOW);
    }
}
