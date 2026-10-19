package gg.ovr.api.card;

import org.junit.jupiter.api.Test;

import static gg.ovr.api.card.TestCards.UPDATED_AT;
import static gg.ovr.api.card.TestCards.card;
import static org.assertj.core.api.Assertions.assertThat;

class InMemoryCardStoreTest {

    private final InMemoryCardStore store = new InMemoryCardStore();

    @Test
    void unknownCardIsEmpty() {
        assertThat(store.find("puuid-1")).isEmpty();
    }

    @Test
    void latestCardWins() {
        assertThat(store.save(card("puuid-1", 50, UPDATED_AT))).isTrue();
        assertThat(store.save(card("puuid-1", 61, UPDATED_AT.plusSeconds(1)))).isTrue();

        assertThat(store.find("puuid-1")).hasValueSatisfying(card -> assertThat(card.ovr()).isEqualTo(61));
    }

    @Test
    void olderCardIsIgnored() {
        store.save(card("puuid-1", 61, UPDATED_AT.plusSeconds(1)));

        assertThat(store.save(card("puuid-1", 50, UPDATED_AT))).isFalse();
        assertThat(store.find("puuid-1")).hasValueSatisfying(card -> assertThat(card.ovr()).isEqualTo(61));
    }
}
