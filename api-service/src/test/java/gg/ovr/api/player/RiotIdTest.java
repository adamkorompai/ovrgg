package gg.ovr.api.player;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class RiotIdTest {

    @Test
    void trimsSurroundingSpaces() {
        RiotId riotId = new RiotId("  Hide on bush ", " KR1 ");

        assertThat(riotId.gameName()).isEqualTo("Hide on bush");
        assertThat(riotId.tagLine()).isEqualTo("KR1");
    }

    @Test
    void acceptsNonLatinGameName() {
        assertThat(new RiotId("페이커", "KR1").gameName()).isEqualTo("페이커");
    }

    @ParameterizedTest
    @CsvSource(nullValues = "NULL", value = {
            "NULL, EUW",
            "Alice, NULL",
            "'  ', EUW",
            "Al, EUW",
            "Seventeen_chars_x, EUW",
            "Alice, EU",
            "Alice, EUWEST",
            "Alice, E#W",
    })
    void rejectsInvalidRiotId(String gameName, String tagLine) {
        assertThatIllegalArgumentException().isThrownBy(() -> new RiotId(gameName, tagLine));
    }
}
