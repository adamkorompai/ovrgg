package gg.ovr.api.card;

import gg.ovr.common.card.CardUpdated;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Open SSE connections per puuid. A connection is forgotten when it completes, times out or fails.
 */
public class CardStreamRegistry {

    static final String EVENT_NAME = "card";

    private final Map<String, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final Duration timeout;

    public CardStreamRegistry(Duration timeout) {
        this.timeout = timeout;
    }

    public SseEmitter subscribe(String puuid) {
        SseEmitter emitter = new SseEmitter(timeout.toMillis());
        // compute(): add and remove run under the same map lock, so an emitter never lands in a dropped set.
        emitters.compute(puuid, (key, set) -> {
            Set<SseEmitter> subscribers = set == null ? ConcurrentHashMap.newKeySet() : set;
            subscribers.add(emitter);
            return subscribers;
        });
        emitter.onCompletion(() -> remove(puuid, emitter));
        emitter.onTimeout(() -> remove(puuid, emitter));
        emitter.onError(e -> remove(puuid, emitter));
        return emitter;
    }

    public void sendToSubscribers(CardUpdated card) {
        emitters.getOrDefault(card.puuid(), Set.of()).forEach(emitter -> send(emitter, card));
    }

    public void send(SseEmitter emitter, CardUpdated card) {
        try {
            emitter.send(SseEmitter.event().name(EVENT_NAME).data(card));
        } catch (IOException | IllegalStateException e) {
            // Client gone or emitter already completed.
            emitter.completeWithError(e);
        }
    }

    private void remove(String puuid, SseEmitter emitter) {
        emitters.computeIfPresent(puuid, (key, set) -> {
            set.remove(emitter);
            return set.isEmpty() ? null : set;
        });
    }
}
