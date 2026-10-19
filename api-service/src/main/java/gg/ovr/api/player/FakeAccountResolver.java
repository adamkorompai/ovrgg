package gg.ovr.api.player;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Every Riot ID exists and always maps to the same puuid. Case-insensitive, like Riot IDs.
 */
public class FakeAccountResolver implements AccountResolver {

    @Override
    public Optional<String> findPuuid(RiotId riotId) {
        String key = "fake-account:" + riotId.gameName().toLowerCase(Locale.ROOT)
                + "#" + riotId.tagLine().toLowerCase(Locale.ROOT);
        return Optional.of(UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8)).toString());
    }
}
