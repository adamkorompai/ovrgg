package gg.ovr.api.card;

import gg.ovr.common.card.CardUpdated;

import java.util.Optional;

/**
 * Latest card of each player, as read from {@code card.updated}. In memory for now; a Postgres
 * implementation can replace it without touching the listener or the controller.
 */
public interface CardStore {

    /**
     * Keeps the card unless a more recent one ({@code updatedAt}) is already stored.
     *
     * @return true if the card is now the stored one
     */
    boolean save(CardUpdated card);

    Optional<CardUpdated> find(String puuid);
}
