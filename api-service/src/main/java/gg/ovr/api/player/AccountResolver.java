package gg.ovr.api.player;

import java.util.Optional;

/**
 * Riot ID → puuid: ACCOUNT-V1 in production (roadmap step 3), generated in dev and tests.
 */
@FunctionalInterface
public interface AccountResolver {

    Optional<String> findPuuid(RiotId riotId);
}
