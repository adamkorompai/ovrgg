package gg.ovr.api.card;

import gg.ovr.common.card.CardUpdated;
import gg.ovr.common.rating.Attributes;

import java.time.Instant;
import java.util.List;

final class TestCards {

    static final Instant UPDATED_AT = Instant.parse("2026-10-19T12:00:00Z");

    private TestCards() {
    }

    static CardUpdated card(String puuid, int ovr) {
        return card(puuid, ovr, UPDATED_AT);
    }

    static CardUpdated card(String puuid, int ovr, Instant updatedAt) {
        return new CardUpdated(puuid, "Alice", "EUW", ovr, new Attributes(48, 56, 35, 54, 64, 37),
                12, false, List.of(7.2, 6.1), updatedAt);
    }
}
