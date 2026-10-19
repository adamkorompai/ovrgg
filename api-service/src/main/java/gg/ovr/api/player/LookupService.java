package gg.ovr.api.player;

import gg.ovr.common.player.PlayerLookupRequested;

import java.time.Clock;
import java.util.Optional;

public class LookupService {

    private final AccountResolver accounts;
    private final LookupPublisher publisher;
    private final Clock clock;

    public LookupService(AccountResolver accounts, LookupPublisher publisher, Clock clock) {
        this.accounts = accounts;
        this.publisher = publisher;
        this.clock = clock;
    }

    /**
     * @return the player's puuid, the key to follow the card; empty if no account has this Riot ID
     */
    public Optional<String> requestLookup(RiotId riotId) {
        Optional<String> puuid = accounts.findPuuid(riotId);
        puuid.ifPresent(id -> publisher.publish(
                new PlayerLookupRequested(id, riotId.gameName(), riotId.tagLine(), clock.instant())));
        return puuid;
    }
}
