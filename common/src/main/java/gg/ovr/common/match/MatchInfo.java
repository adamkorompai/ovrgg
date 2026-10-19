package gg.ovr.common.match;

import java.util.List;

/**
 * @param gameCreation epoch millis
 * @param gameDuration seconds
 * @param queueId      see {@link Queue}
 */
public record MatchInfo(
        long gameId,
        long gameCreation,
        long gameDuration,
        int queueId,
        String gameMode,
        String platformId,
        List<Participant> participants) {

    public MatchInfo {
        participants = List.copyOf(participants);
    }
}
