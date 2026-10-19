package gg.ovr.api.card;

import gg.ovr.common.card.CardUpdated;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lost on restart, rebuilt by replaying the compacted {@code card.updated} topic.
 */
public class InMemoryCardStore implements CardStore {

    private final Map<String, CardUpdated> cards = new ConcurrentHashMap<>();

    @Override
    public boolean save(CardUpdated card) {
        // merge() is atomic per key: two listener threads cannot both win.
        return cards.merge(card.puuid(), card,
                (stored, received) -> received.updatedAt().isBefore(stored.updatedAt()) ? stored : received) == card;
    }

    @Override
    public Optional<CardUpdated> find(String puuid) {
        return Optional.ofNullable(cards.get(puuid));
    }
}
