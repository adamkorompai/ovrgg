package gg.ovr.common.player;

import java.time.Instant;
import java.util.Objects;

/**
 * Value of {@code player.lookup-requested} (key: puuid). Someone searched this player on the site.
 */
public record PlayerLookupRequested(String puuid, String gameName, String tagLine, Instant requestedAt) {

    public PlayerLookupRequested {
        Objects.requireNonNull(puuid, "puuid");
        Objects.requireNonNull(gameName, "gameName");
        Objects.requireNonNull(tagLine, "tagLine");
        Objects.requireNonNull(requestedAt, "requestedAt");
    }
}
