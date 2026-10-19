package gg.ovr.common.match;

/**
 * End-of-game stats of one player (MATCH-V5 {@code info.participants[]}).
 *
 * @param teamId       100 (blue) or 200 (red)
 * @param teamPosition TOP, JUNGLE, MIDDLE, BOTTOM or UTILITY
 */
public record Participant(
        String puuid,
        String riotIdGameName,
        String riotIdTagline,
        String championName,
        int teamId,
        String teamPosition,
        boolean win,
        boolean gameEndedInEarlySurrender,
        int kills,
        int deaths,
        int assists,
        int totalMinionsKilled,
        int neutralMinionsKilled,
        int goldEarned,
        int totalDamageDealtToChampions,
        int totalDamageTaken,
        int damageDealtToBuildings,
        int damageDealtToObjectives,
        int visionScore,
        int wardsPlaced,
        int wardsKilled,
        int visionWardsBoughtInGame,
        int timeCCingOthers,
        int totalTimeSpentDead,
        boolean firstBloodKill,
        boolean firstBloodAssist,
        boolean firstTowerKill,
        boolean firstTowerAssist,
        Challenges challenges) {
}
