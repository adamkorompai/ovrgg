package gg.ovr.common.match;

import java.util.List;

/**
 * @param participants puuids of the 10 players, in the same order as {@link MatchInfo#participants()}
 */
public record MatchMetadata(String matchId, List<String> participants) {

    public MatchMetadata {
        participants = List.copyOf(participants);
    }
}
