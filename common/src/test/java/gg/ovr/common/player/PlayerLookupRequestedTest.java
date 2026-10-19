package gg.ovr.common.player;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatNullPointerException;

class PlayerLookupRequestedTest {

    @Test
    void requiresPuuid() {
        assertThatNullPointerException()
                .isThrownBy(() -> new PlayerLookupRequested(null, "Faker", "KR1", Instant.EPOCH))
                .withMessageContaining("puuid");
    }
}
