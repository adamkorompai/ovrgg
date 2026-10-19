package gg.ovr.card.rating;

import gg.ovr.common.card.CardUpdated;

/**
 * Sends a card to {@code card.updated}.
 */
public interface CardPublisher {

    /**
     * Returns only once the broker has acknowledged the card.
     *
     * @throws RuntimeException if the card could not be published
     */
    void publish(CardUpdated card);
}
