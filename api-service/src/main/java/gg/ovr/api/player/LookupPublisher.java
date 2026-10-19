package gg.ovr.api.player;

import gg.ovr.common.player.PlayerLookupRequested;

/**
 * Sends a lookup to {@code player.lookup-requested}.
 */
@FunctionalInterface
public interface LookupPublisher {

    /**
     * Returns only once the broker has acknowledged the lookup, so a 202 means it will be processed.
     *
     * @throws RuntimeException if the lookup could not be published
     */
    void publish(PlayerLookupRequested lookup);
}
