package gg.ovr.common.card;

import gg.ovr.common.rating.Attributes;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Value of {@code card.updated} (key: puuid, compacted topic: only the latest card of a player is kept).
 *
 * @param ovr            1–99 (SCORING.md §8)
 * @param matchesCounted matches that fed the rating
 * @param provisional    too few matches for an official card (SCORING.md §3)
 * @param recentRatings  latest match ratings out of 10, most recent first
 */
public record CardUpdated(
        String puuid,
        String gameName,
        String tagLine,
        int ovr,
        Attributes attributes,
        int matchesCounted,
        boolean provisional,
        List<Double> recentRatings,
        Instant updatedAt) {

    public CardUpdated {
        Objects.requireNonNull(puuid, "puuid");
        Objects.requireNonNull(attributes, "attributes");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (ovr < 1 || ovr > 99) {
            throw new IllegalArgumentException("ovr must be between 1 and 99, was " + ovr);
        }
        recentRatings = List.copyOf(recentRatings);
    }
}
