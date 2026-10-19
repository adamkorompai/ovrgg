package gg.ovr.api.card;

import gg.ovr.common.card.CardUpdated;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
class CardController {

    private final CardFeed feed;

    CardController(CardFeed feed) {
        this.feed = feed;
    }

    @GetMapping("/api/cards/{puuid}")
    ResponseEntity<CardUpdated> card(@PathVariable String puuid) {
        return ResponseEntity.of(feed.find(puuid));
    }

    /**
     * Server-Sent Events: the current card if known, then each update (event name "card").
     */
    @GetMapping(path = "/api/cards/{puuid}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter stream(@PathVariable String puuid) {
        return feed.subscribe(puuid);
    }
}
