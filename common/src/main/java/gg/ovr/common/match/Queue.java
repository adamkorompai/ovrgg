package gg.ovr.common.match;

import java.util.Arrays;

/**
 * Riot queue ids we care about. Only ranked queues feed the OVR rating (SCORING.md §3).
 */
public enum Queue {
    NORMAL_DRAFT(400, false),
    RANKED_SOLO_DUO(420, true),
    RANKED_FLEX(440, true),
    ARAM(450, false);

    private final int id;
    private final boolean ranked;

    Queue(int id, boolean ranked) {
        this.id = id;
        this.ranked = ranked;
    }

    public int id() {
        return id;
    }

    public boolean isRanked() {
        return ranked;
    }

    public static boolean isRankedQueueId(int queueId) {
        return Arrays.stream(values()).anyMatch(q -> q.id == queueId && q.ranked);
    }
}
