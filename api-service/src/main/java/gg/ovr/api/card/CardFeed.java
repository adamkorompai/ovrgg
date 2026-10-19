package gg.ovr.api.card;

import gg.ovr.common.card.CardUpdated;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Optional;

/**
 * Where cards from {@code card.updated} land: stored, then pushed to open streams.
 */
public class CardFeed {

    private final CardStore store;
    private final CardStreamRegistry streams;

    public CardFeed(CardStore store, CardStreamRegistry streams) {
        this.store = store;
        this.streams = streams;
    }

    /**
     * An older card than the stored one (out-of-order delivery) is neither stored nor pushed.
     */
    public void onCardUpdated(CardUpdated card) {
        if (store.save(card)) {
            streams.sendToSubscribers(card);
        }
    }

    /**
     * Subscribes first, then sends the known card, so no update is missed in between. A client may
     * then get the same card twice, or an older one right after a newer one: it keeps the latest
     * {@code updatedAt}.
     */
    public SseEmitter subscribe(String puuid) {
        SseEmitter emitter = streams.subscribe(puuid);
        store.find(puuid).ifPresent(card -> streams.send(emitter, card));
        return emitter;
    }

    public Optional<CardUpdated> find(String puuid) {
        return store.find(puuid);
    }
}
