package gg.ovr.api.player;

import gg.ovr.common.player.PlayerLookupRequested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class LookupServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-19T12:00:00Z");

    private final List<PlayerLookupRequested> published = new ArrayList<>();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void publishesLookupAndReturnsPuuid() {
        LookupService service = new LookupService(riotId -> Optional.of("puuid-1"), published::add, clock);

        Optional<String> puuid = service.requestLookup(new RiotId("Alice", "EUW"));

        assertThat(puuid).contains("puuid-1");
        assertThat(published).containsExactly(new PlayerLookupRequested("puuid-1", "Alice", "EUW", NOW));
    }

    @Test
    void unknownAccountPublishesNothing() {
        LookupService service = new LookupService(riotId -> Optional.empty(), published::add, clock);

        assertThat(service.requestLookup(new RiotId("Nobody", "EUW"))).isEmpty();
        assertThat(published).isEmpty();
    }
}
