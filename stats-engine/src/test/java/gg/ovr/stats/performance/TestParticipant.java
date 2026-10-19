package gg.ovr.stats.performance;

import gg.ovr.common.match.Challenges;
import gg.ovr.common.match.Participant;

/**
 * Mutable builder for {@link Participant}. Defaults give 50 on every provisional attribute
 * over a 40 minute match.
 */
class TestParticipant {

    String puuid = "puuid-1";
    String teamPosition = "MIDDLE";
    boolean win = true;
    int kills = 4;
    int deaths = 10;
    int assists = 6;
    int totalMinionsKilled = 160;
    int neutralMinionsKilled = 40;
    int visionScore = 60;
    int damageDealtToObjectives = 20_000;
    Double teamDamagePercentage = 0.2;
    Double killParticipation = 0.5;
    boolean withChallenges = true;

    Participant build() {
        Challenges challenges = withChallenges
                ? new Challenges(teamDamagePercentage, 0, 0, null, null, null, null, null,
                killParticipation, null, null, null, null)
                : null;
        return new Participant(puuid, "Player", "TAG", "Ahri", 100, teamPosition, win, false,
                kills, deaths, assists, totalMinionsKilled, neutralMinionsKilled, 10_000, 20_000, 20_000,
                0, damageDealtToObjectives, visionScore, 10, 2, 1, 10, 60,
                false, false, false, false, challenges);
    }
}
