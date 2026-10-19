package gg.ovr.ingestion.match;

import gg.ovr.common.match.Participant;
import gg.ovr.common.match.Queue;
import gg.ovr.common.match.RiotMatch;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.within;

class FakeMatchSourceTest {

    private static final String PUUID = "test-puuid-alice";

    private final FakeMatchSource source = new FakeMatchSource();

    @Test
    void findRecentMatchIdsReturnsRequestedCountOfDistinctIds() {
        assertThat(source.findRecentMatchIds(PUUID, 20)).hasSize(20).doesNotHaveDuplicates();
    }

    @Test
    void findRecentMatchIdsIsDeterministicPerPlayer() {
        assertThat(source.findRecentMatchIds(PUUID, 5)).isEqualTo(source.findRecentMatchIds(PUUID, 5));
    }

    @Test
    void differentPlayersGetDifferentMatchIds() {
        assertThat(source.findRecentMatchIds(PUUID, 5))
                .doesNotContainAnyElementsOf(source.findRecentMatchIds("test-puuid-bob", 5));
    }

    @Test
    void findMatchReturnsEmptyForMalformedId() {
        assertThat(source.findMatch("EUW1_123")).isEmpty();
        assertThat(source.findMatch("FAKE_notanumber_" + PUUID)).isEmpty();
        assertThat(source.findMatch("FAKE_3_")).isEmpty();
        assertThat(source.findMatch("FAKE_99999999999_" + PUUID)).isEmpty();
    }

    @Test
    void findMatchReturnsTenPlayersWithFiveRolesPerTeamAndOneWinningTeam() {
        RiotMatch match = firstMatch();

        List<Participant> participants = match.info().participants();
        assertThat(participants).hasSize(10);
        Map<Integer, List<Participant>> byTeam = participants.stream()
                .collect(Collectors.groupingBy(Participant::teamId));
        assertThat(byTeam).containsOnlyKeys(100, 200);
        byTeam.values().forEach(team -> assertThat(team)
                .extracting(Participant::teamPosition)
                .containsExactlyInAnyOrder("TOP", "JUNGLE", "MIDDLE", "BOTTOM", "UTILITY"));
        assertThat(byTeam.get(100).getFirst().win()).isNotEqualTo(byTeam.get(200).getFirst().win());
        byTeam.values().forEach(team -> assertThat(team).extracting(Participant::win).containsOnly(team.getFirst().win()));
    }

    @Test
    void findMatchContainsRequestedPlayer() {
        RiotMatch match = firstMatch();

        assertThat(match.metadata().matchId()).isEqualTo(source.findRecentMatchIds(PUUID, 1).getFirst());
        assertThat(match.info().participants()).extracting(Participant::puuid).contains(PUUID);
    }

    @Test
    void findMatchIsDeterministic() {
        String matchId = source.findRecentMatchIds(PUUID, 1).getFirst();

        assertThat(source.findMatch(matchId)).isEqualTo(new FakeMatchSource().findMatch(matchId));
    }

    @Test
    void metadataParticipantsMatchInfoParticipants() {
        RiotMatch match = firstMatch();

        assertThat(match.metadata().participants())
                .containsExactlyElementsOf(match.info().participants().stream().map(Participant::puuid).toList())
                .doesNotHaveDuplicates();
    }

    @Test
    void generatedStatsAreConsistent() {
        for (RiotMatch match : matches(200)) {
            double minutes = match.info().gameDuration() / 60.0;
            for (Participant p : match.info().participants()) {
                int teamKills = teamOf(match, p.teamId()).stream().mapToInt(Participant::kills).sum();
                assertThat(p.kills()).isNotNegative();
                assertThat(p.deaths()).isNotNegative();
                assertThat(p.assists()).isNotNegative();
                assertThat(p.kills() + p.assists()).isLessThanOrEqualTo(teamKills);
                assertThat(p.totalMinionsKilled()).isNotNegative();
                assertThat(p.goldEarned()).isPositive();
                assertThat(p.totalTimeSpentDead()).isBetween(0, (int) match.info().gameDuration());
                assertThat(p.damageDealtToObjectives()).isGreaterThanOrEqualTo(p.damageDealtToBuildings());
                assertThat(p.challenges().killParticipation()).isBetween(0.0, 1.0);
                assertThat(p.challenges().teamDamagePercentage()).isBetween(0.0, 1.0);
                assertThat(p.challenges().soloKills()).isLessThanOrEqualTo(p.kills());
                assertThat(p.challenges().killsNearEnemyTurret()).isLessThanOrEqualTo(p.kills());
                assertThat(p.challenges().laneMinionsFirst10Minutes()).isLessThanOrEqualTo(p.totalMinionsKilled());
                assertThat(p.challenges().goldPerMinute()).isCloseTo(p.goldEarned() / minutes, within(0.01));
            }
        }
    }

    @Test
    void eachTeamsKillsAreTheOtherTeamsDeaths() {
        for (RiotMatch match : matches(200)) {
            assertThat(teamOf(match, 100).stream().mapToInt(Participant::kills).sum())
                    .isEqualTo(teamOf(match, 200).stream().mapToInt(Participant::deaths).sum());
            assertThat(teamOf(match, 200).stream().mapToInt(Participant::kills).sum())
                    .isEqualTo(teamOf(match, 100).stream().mapToInt(Participant::deaths).sum());
        }
    }

    @Test
    void firstBloodAndFirstTowerHappenOncePerMatch() {
        for (RiotMatch match : matches(50).stream().filter(m -> m.info().gameDuration() >= 300).toList()) {
            assertThat(match.info().participants()).filteredOn(Participant::firstBloodKill).hasSize(1);
            assertThat(match.info().participants()).filteredOn(Participant::firstTowerKill).hasSize(1);
        }
    }

    @Test
    void afkPlayerHasNearZeroActivityEverywhere() {
        List<Participant> afkPlayers = matches(200).stream()
                .filter(FakeMatchSourceTest::requestedPlayerIsAfk)
                .map(FakeMatchSourceTest::requestedPlayer)
                .toList();

        assertThat(afkPlayers).isNotEmpty().allSatisfy(p -> {
            assertThat(p.assists()).isZero();
            assertThat(p.wardsPlaced()).isLessThanOrEqualTo(2);
            assertThat(p.timeCCingOthers()).isLessThanOrEqualTo(30);
            assertThat(p.damageDealtToBuildings()).isLessThanOrEqualTo(200);
            assertThat(p.damageDealtToObjectives()).isLessThanOrEqualTo(400);
            assertThat(p.challenges().effectiveHealAndShielding()).isLessThanOrEqualTo(100.0);
            assertThat(p.challenges().turretPlatesTaken()).isZero();
        });
    }

    @Test
    void remakesHaveFewKillsAndDeaths() {
        matches(200).stream()
                .filter(m -> m.info().gameDuration() < 300)
                .flatMap(m -> m.info().participants().stream())
                .forEach(p -> {
                    assertThat(p.kills()).isLessThanOrEqualTo(3);
                    assertThat(p.deaths()).isLessThanOrEqualTo(3);
                });
    }

    @Test
    void findRecentMatchIdsRejectsInvalidArguments() {
        assertThatNullPointerException().isThrownBy(() -> source.findRecentMatchIds(null, 5));
        assertThatIllegalArgumentException().isThrownBy(() -> source.findRecentMatchIds(PUUID, -1));
        assertThatIllegalArgumentException().isThrownBy(() -> source.findRecentMatchIds(PUUID, 101));
    }

    @Test
    void supportsFarmLessAndWardMoreThanOtherRoles() {
        List<Participant> all = matches(200).stream().flatMap(m -> m.info().participants().stream()).toList();
        Map<Boolean, List<Participant>> bySupport = all.stream()
                .collect(Collectors.partitioningBy(p -> p.teamPosition().equals("UTILITY")));

        assertThat(average(bySupport.get(true), FakeMatchSourceTest::totalCs))
                .isLessThan(0.5 * average(bySupport.get(false), FakeMatchSourceTest::totalCs));
        assertThat(average(bySupport.get(true), p -> p.challenges().visionScorePerMinute()))
                .isGreaterThan(1.5 * average(bySupport.get(false), p -> p.challenges().visionScorePerMinute()));
    }

    @Test
    void aSmallShareOfMatchesAreOnesTheRatingMustExclude() {
        List<RiotMatch> matches = matches(200);

        long remakes = matches.stream().filter(m -> m.info().gameDuration() < 300).count();
        long unranked = matches.stream().filter(m -> !Queue.isRankedQueueId(m.info().queueId())).count();
        long withAfk = matches.stream().filter(FakeMatchSourceTest::requestedPlayerIsAfk).count();
        long excluded = matches.stream()
                .filter(m -> m.info().gameDuration() < 300
                        || !Queue.isRankedQueueId(m.info().queueId())
                        || requestedPlayerIsAfk(m))
                .count();

        assertThat(remakes).isPositive();
        assertThat(unranked).isPositive();
        assertThat(withAfk).isPositive();
        assertThat(excluded).isBetween(15L, 30L);
    }

    @Test
    void remakesAreFlaggedAsEarlySurrender() {
        matches(200).stream()
                .filter(m -> m.info().gameDuration() < 300)
                .flatMap(m -> m.info().participants().stream())
                .forEach(p -> assertThat(p.gameEndedInEarlySurrender()).isTrue());
    }

    /** Test-only heuristic; the real AFK filter belongs to stats-engine (step 4). */
    private static boolean requestedPlayerIsAfk(RiotMatch match) {
        return match.info().gameDuration() >= 300 && requestedPlayer(match).totalDamageDealtToChampions() < 500;
    }

    private static List<Participant> teamOf(RiotMatch match, int teamId) {
        return match.info().participants().stream().filter(p -> p.teamId() == teamId).toList();
    }

    private static Participant requestedPlayer(RiotMatch match) {
        return match.info().participants().stream().filter(p -> p.puuid().equals(PUUID)).findFirst().orElseThrow();
    }

    private static double totalCs(Participant p) {
        return p.totalMinionsKilled() + p.neutralMinionsKilled();
    }

    private static double average(List<Participant> participants, ToDoubleFunction<Participant> metric) {
        return participants.stream().mapToDouble(metric).average().orElseThrow();
    }

    private RiotMatch firstMatch() {
        return source.findMatch(source.findRecentMatchIds(PUUID, 1).getFirst()).orElseThrow();
    }

    /** Builds ids directly (format FAKE_<index>_<puuid>) to sample more than {@link FakeMatchSource#MAX_COUNT}. */
    private List<RiotMatch> matches(int count) {
        return IntStream.range(0, count)
                .mapToObj(index -> source.findMatch("FAKE_" + index + "_" + PUUID).orElseThrow())
                .toList();
    }
}
