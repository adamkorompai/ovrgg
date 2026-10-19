package gg.ovr.ingestion.lookup;

import gg.ovr.common.kafka.Topics;
import gg.ovr.common.player.PlayerLookupRequested;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * The offset is committed only once {@link LookupHandler#handle} returns: a failed lookup is read again.
 */
@Component
class LookupRequestedListener {

    private final LookupHandler handler;

    LookupRequestedListener(LookupHandler handler) {
        this.handler = handler;
    }

    @KafkaListener(topics = Topics.PLAYER_LOOKUP_REQUESTED)
    void onLookupRequested(PlayerLookupRequested lookup) {
        handler.handle(lookup);
    }
}
