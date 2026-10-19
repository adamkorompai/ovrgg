package gg.ovr.common.match;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class QueueTest {

    @Test
    void soloDuoAndFlexAreRanked() {
        assertThat(Queue.RANKED_SOLO_DUO.isRanked()).isTrue();
        assertThat(Queue.RANKED_FLEX.isRanked()).isTrue();
    }

    @Test
    void normalAndAramAreNotRanked() {
        assertThat(Queue.NORMAL_DRAFT.isRanked()).isFalse();
        assertThat(Queue.ARAM.isRanked()).isFalse();
    }

    @Test
    void isRankedQueueIdMatchesRiotQueueIds() {
        assertThat(Queue.isRankedQueueId(420)).isTrue();
        assertThat(Queue.isRankedQueueId(440)).isTrue();
        assertThat(Queue.isRankedQueueId(450)).isFalse();
        assertThat(Queue.isRankedQueueId(9999)).isFalse();
    }
}
