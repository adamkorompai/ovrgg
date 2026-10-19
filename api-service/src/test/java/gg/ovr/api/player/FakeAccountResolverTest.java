package gg.ovr.api.player;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FakeAccountResolverTest {

    private final FakeAccountResolver resolver = new FakeAccountResolver();

    @Test
    void sameRiotIdGivesSamePuuid() {
        assertThat(resolver.findPuuid(new RiotId("Alice", "EUW")))
                .isEqualTo(resolver.findPuuid(new RiotId("Alice", "EUW")))
                .isPresent();
    }

    @Test
    void riotIdIsCaseInsensitive() {
        assertThat(resolver.findPuuid(new RiotId("ALICE", "euw")))
                .isEqualTo(resolver.findPuuid(new RiotId("alice", "EUW")));
    }

    @Test
    void differentRiotIdsGiveDifferentPuuids() {
        assertThat(resolver.findPuuid(new RiotId("Alice", "EUW")))
                .isNotEqualTo(resolver.findPuuid(new RiotId("Alice", "NA1")));
    }
}
