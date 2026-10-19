package gg.ovr.common.match;

/**
 * MATCH-V5 {@code participants[].challenges}. Riot omits some of these fields
 * (e.g. no lane opponent), so they are nullable: absent must not be read as 0.
 */
public record Challenges(
        Double teamDamagePercentage,
        Integer soloKills,
        Integer killsNearEnemyTurret,
        Integer laneMinionsFirst10Minutes,
        Integer maxCsAdvantageOnLaneOpponent,
        Double goldPerMinute,
        Double visionScorePerMinute,
        Double visionScoreAdvantageLaneOpponent,
        Double killParticipation,
        Double effectiveHealAndShielding,
        Integer survivedSingleDigitHpCount,
        Integer turretPlatesTaken,
        Integer epicMonsterSteals) {
}
