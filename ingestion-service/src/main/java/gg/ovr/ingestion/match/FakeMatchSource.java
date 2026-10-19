package gg.ovr.ingestion.match;

import gg.ovr.common.match.Challenges;
import gg.ovr.common.match.MatchInfo;
import gg.ovr.common.match.MatchMetadata;
import gg.ovr.common.match.Participant;
import gg.ovr.common.match.Queue;
import gg.ovr.common.match.RiotMatch;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.IntPredicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

/**
 * Generated, deterministic matches for dev and tests.
 * Match ids embed the requested puuid ({@code FAKE_<index>_<puuid>}) so a match can be
 * regenerated from its id alone, without storing anything.
 */
public class FakeMatchSource implements MatchSource {

    /** Same upper bound as MATCH-V5 {@code by-puuid/{puuid}/ids?count=}. */
    static final int MAX_COUNT = 100;

    private static final String PREFIX = "FAKE_";
    private static final Pattern MATCH_ID = Pattern.compile("FAKE_(\\d{1,9})_(.+)");
    private static final List<Integer> TEAMS = List.of(100, 200);
    private static final long EPOCH_2026 = 1_767_225_600_000L;
    private static final double ASSIST_CHANCE = 0.35;

    @Override
    public List<String> findRecentMatchIds(String puuid, int count) {
        Objects.requireNonNull(puuid, "puuid");
        if (count < 0 || count > MAX_COUNT) {
            throw new IllegalArgumentException("count must be between 0 and " + MAX_COUNT + ", was " + count);
        }
        return IntStream.range(0, count)
                .mapToObj(index -> PREFIX + index + "_" + puuid)
                .toList();
    }

    @Override
    public Optional<RiotMatch> findMatch(String matchId) {
        Matcher matcher = MATCH_ID.matcher(matchId);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        return Optional.of(generate(matchId, Integer.parseInt(matcher.group(1)), matcher.group(2)));
    }

    private RiotMatch generate(String matchId, int index, String puuid) {
        long seed = seed(matchId);
        Random random = new Random(seed);
        MatchKind kind = MatchKind.roll(random);
        long durationSeconds = kind == MatchKind.REMAKE ? 180 + random.nextInt(120) : 1200 + random.nextInt(1200);
        double minutes = durationSeconds / 60.0;
        Queue queue = kind == MatchKind.UNRANKED
                ? (random.nextBoolean() ? Queue.NORMAL_DRAFT : Queue.ARAM)
                : (random.nextInt(4) == 0 ? Queue.RANKED_FLEX : Queue.RANKED_SOLO_DUO);
        int winningTeam = TEAMS.get(random.nextInt(2));
        int playerSlot = random.nextInt(10);
        int afkSlot = kind == MatchKind.PLAYER_AFK ? playerSlot : -1;

        List<RawStats> raw = new ArrayList<>();
        for (int slot = 0; slot < 10; slot++) {
            raw.add(slot == afkSlot
                    ? RawStats.afk(minutes)
                    : RawStats.generate(random, RoleProfile.forSlot(slot), minutes));
        }
        Fights fights = Fights.generate(random, raw, kind == MatchKind.REMAKE);

        List<Participant> participants = new ArrayList<>();
        for (int slot = 0; slot < 10; slot++) {
            int teamId = TEAMS.get(slot / 5);
            String slotPuuid = slot == playerSlot ? puuid : fakePuuid(matchId, slot);
            participants.add(toParticipant(random, slotPuuid, slot, teamId, teamId == winningTeam,
                    kind == MatchKind.REMAKE, raw, fights, minutes, durationSeconds));
        }

        MatchInfo info = new MatchInfo(
                seed & Long.MAX_VALUE,
                EPOCH_2026 - index * 3_600_000L,
                durationSeconds,
                queue.id(),
                queue == Queue.ARAM ? "ARAM" : "CLASSIC",
                "EUW1",
                participants);
        return new RiotMatch(new MatchMetadata(matchId, participants.stream().map(Participant::puuid).toList()), info);
    }

    private static Participant toParticipant(Random random, String puuid, int slot, int teamId, boolean win,
                                             boolean earlySurrender, List<RawStats> raw, Fights fights,
                                             double minutes, long durationSeconds) {
        RawStats self = raw.get(slot);
        RawStats laneOpponent = raw.get(laneOpponentSlot(slot));
        RoleProfile role = RoleProfile.forSlot(slot);
        double activity = self.activity();
        int kills = fights.kills()[slot];
        int deaths = fights.deaths()[slot];
        int assists = fights.assists()[slot];
        int teamKills = fights.teamKills(slot);
        int teamDamage = raw.subList(slot / 5 * 5, slot / 5 * 5 + 5).stream().mapToInt(RawStats::damage).sum();
        int cs = self.totalCs();
        int gold = (int) (minutes * 300) + cs * 20 + kills * 300 + assists * 150;
        int timeSpentDead = (int) Math.min(durationSeconds, deaths * (15L + random.nextInt(30)));
        double killParticipation = teamKills == 0 ? 0.0 : (double) (kills + assists) / teamKills;
        int damageToBuildings = (int) (random.nextInt(8000) * activity);

        Challenges challenges = new Challenges(
                (double) self.damage() / teamDamage,
                random.nextInt(kills / 2 + 1),
                random.nextInt(kills / 3 + 1),
                Math.min(self.laneCs(), (int) ((role.laneMinionsAt10 + random.nextInt(15)) * activity)),
                Math.max(0, cs - laneOpponent.totalCs()),
                gold / minutes,
                self.visionScore() / minutes,
                // Antisymmetric, so two lane opponents never both have the advantage.
                (double) (self.visionScore() - laneOpponent.visionScore())
                        / Math.max(1, Math.max(self.visionScore(), laneOpponent.visionScore())),
                killParticipation,
                minutes * random.nextDouble() * role.maxHealShieldPerMin * activity,
                (int) (random.nextInt(3) * activity),
                (int) (random.nextInt(5) * activity),
                random.nextInt(20) == 0 ? (int) activity : 0);

        return new Participant(
                puuid,
                "Player" + slot,
                "FAKE",
                "Champion" + slot,
                teamId,
                role.name(),
                win,
                earlySurrender,
                kills,
                deaths,
                assists,
                self.laneCs(),
                self.jungleCs(),
                gold,
                self.damage(),
                (int) (minutes * (500 + random.nextInt(700))),
                damageToBuildings,
                // Riot counts buildings as objectives.
                damageToBuildings + (int) (random.nextInt(12000) * activity),
                self.visionScore(),
                (int) (minutes * role.wardsPerMin * (0.7 + random.nextDouble() * 0.6) * activity),
                (int) (minutes * random.nextDouble() * 0.2 * activity),
                (int) (random.nextInt(6) * activity),
                (int) (minutes * random.nextInt(role.maxCcPerMin + 1) * activity),
                timeSpentDead,
                slot == fights.firstBlood(),
                fights.firstBloodAssists().contains(slot),
                slot == fights.firstTower(),
                fights.firstTowerAssists().contains(slot),
                challenges);
    }

    private static int laneOpponentSlot(int slot) {
        return (slot + 5) % 10;
    }

    private static long seed(String matchId) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(matchId.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.wrap(hash).getLong();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required on every JVM", e);
        }
    }

    private static String fakePuuid(String matchId, int slot) {
        return UUID.nameUUIDFromBytes((matchId + "#" + slot).getBytes(StandardCharsets.UTF_8)).toString();
    }

    /**
     * Per-player stats that do not depend on other players.
     *
     * @param activity 1.0 for a normal player, 0.0 for an AFK one; scales every activity stat
     */
    private record RawStats(int laneCs, int jungleCs, int damage, int visionScore, int maxKills, double activity) {

        static RawStats generate(Random random, RoleProfile role, double minutes) {
            int cs = (int) (minutes * between(random, role.minCsPerMin, role.maxCsPerMin));
            int jungleCs = (int) (cs * role.jungleCsShare);
            return new RawStats(
                    cs - jungleCs,
                    jungleCs,
                    (int) (minutes * between(random, role.minDamagePerMin, role.maxDamagePerMin)),
                    (int) (minutes * between(random, role.minVisionPerMin, role.maxVisionPerMin)),
                    role.maxKills,
                    1.0);
        }

        /** Near-zero activity: what the AFK filter of SCORING.md §3 must catch. */
        static RawStats afk(double minutes) {
            return new RawStats((int) (minutes * 0.2), 0, (int) (minutes * 10), 0, 0, 0.0);
        }

        int totalCs() {
            return laneCs + jungleCs;
        }

        boolean isAfk() {
            return activity == 0.0;
        }

        private static double between(Random random, double min, double max) {
            return min + random.nextDouble() * (max - min);
        }
    }

    /**
     * Kills, deaths and assists across both teams. Kills are drawn first, then each kill of a team
     * becomes a death on the other team, so the totals always match.
     *
     * @param firstBlood slot of the first blood killer, -1 if nobody got a kill
     * @param firstTower slot of the first tower killer, -1 in a remake
     */
    private record Fights(int[] kills, int[] deaths, int[] assists,
                          int firstBlood, Set<Integer> firstBloodAssists,
                          int firstTower, Set<Integer> firstTowerAssists) {

        static Fights generate(Random random, List<RawStats> raw, boolean remake) {
            int[] kills = new int[10];
            if (remake) {
                // Remakes end around minute 3-5: at most one early kill.
                if (random.nextBoolean()) {
                    kills[random.nextInt(10)] = 1;
                }
            } else {
                for (int slot = 0; slot < 10; slot++) {
                    kills[slot] = random.nextInt(raw.get(slot).maxKills() + 1);
                }
            }

            int[] deaths = new int[10];
            for (int team = 0; team < 2; team++) {
                int enemyKills = teamSum(kills, 1 - team);
                for (int kill = 0; kill < enemyKills; kill++) {
                    deaths[team * 5 + random.nextInt(5)]++;
                }
            }

            int[] assists = new int[10];
            for (int slot = 0; slot < 10; slot++) {
                // Capped so that kills + assists never exceeds the team's kills.
                int maxAssists = teamSum(kills, slot / 5) - kills[slot];
                assists[slot] = raw.get(slot).isAfk() ? 0 : random.nextInt(maxAssists + 1);
            }

            List<Integer> killers = IntStream.range(0, 10).filter(slot -> kills[slot] > 0).boxed().toList();
            int firstBlood = killers.isEmpty() ? -1 : killers.get(random.nextInt(killers.size()));
            Set<Integer> firstBloodAssists = firstBlood < 0
                    ? Set.of()
                    : helpers(random, firstBlood, slot -> assists[slot] > 0);

            List<Integer> active = IntStream.range(0, 10).filter(slot -> !raw.get(slot).isAfk()).boxed().toList();
            int firstTower = remake ? -1 : active.get(random.nextInt(active.size()));
            Set<Integer> firstTowerAssists = firstTower < 0
                    ? Set.of()
                    : helpers(random, firstTower, slot -> !raw.get(slot).isAfk());

            return new Fights(kills, deaths, assists, firstBlood, firstBloodAssists, firstTower, firstTowerAssists);
        }

        int teamKills(int slot) {
            return teamSum(kills, slot / 5);
        }

        private static int teamSum(int[] values, int team) {
            return IntStream.range(team * 5, team * 5 + 5).map(slot -> values[slot]).sum();
        }

        private static Set<Integer> helpers(Random random, int mainSlot, IntPredicate eligible) {
            Set<Integer> helpers = new HashSet<>();
            int team = mainSlot / 5;
            for (int slot = team * 5; slot < team * 5 + 5; slot++) {
                if (slot != mainSlot && eligible.test(slot) && random.nextDouble() < ASSIST_CHANCE) {
                    helpers.add(slot);
                }
            }
            return Set.copyOf(helpers);
        }
    }

    /** About 10% of matches are ones the rating must later filter out (SCORING.md §3). */
    private enum MatchKind {
        NORMAL, REMAKE, UNRANKED, PLAYER_AFK;

        static MatchKind roll(Random random) {
            int roll = random.nextInt(100);
            if (roll < 4) {
                return REMAKE;
            }
            if (roll < 8) {
                return UNRANKED;
            }
            if (roll < 11) {
                return PLAYER_AFK;
            }
            return NORMAL;
        }
    }

    /**
     * Rough per-role stat ranges, enough for role baselines to differ (a support farms little
     * and wards a lot). Not calibrated on real data.
     */
    private enum RoleProfile {
        //       cs/min      jgShare  dmg/min    vision/min  kills cs@10 heal/min wards/min cc/min
        TOP(5.5, 8.0, 0.05, 450, 900, 0.4, 0.9, 10, 55, 50, 0.4, 20),
        JUNGLE(4.5, 6.5, 0.85, 350, 750, 0.6, 1.2, 11, 5, 80, 0.5, 25),
        MIDDLE(6.0, 9.0, 0.05, 550, 1000, 0.4, 0.9, 12, 60, 30, 0.4, 20),
        BOTTOM(6.5, 9.5, 0.03, 550, 1100, 0.3, 0.8, 13, 62, 20, 0.35, 10),
        UTILITY(0.5, 1.8, 0.0, 200, 500, 1.8, 3.0, 4, 8, 400, 1.2, 35);

        private final double minCsPerMin;
        private final double maxCsPerMin;
        private final double jungleCsShare;
        private final double minDamagePerMin;
        private final double maxDamagePerMin;
        private final double minVisionPerMin;
        private final double maxVisionPerMin;
        private final int maxKills;
        private final int laneMinionsAt10;
        private final double maxHealShieldPerMin;
        private final double wardsPerMin;
        private final int maxCcPerMin;

        RoleProfile(double minCsPerMin, double maxCsPerMin, double jungleCsShare,
                    double minDamagePerMin, double maxDamagePerMin,
                    double minVisionPerMin, double maxVisionPerMin,
                    int maxKills, int laneMinionsAt10, double maxHealShieldPerMin,
                    double wardsPerMin, int maxCcPerMin) {
            this.minCsPerMin = minCsPerMin;
            this.maxCsPerMin = maxCsPerMin;
            this.jungleCsShare = jungleCsShare;
            this.minDamagePerMin = minDamagePerMin;
            this.maxDamagePerMin = maxDamagePerMin;
            this.minVisionPerMin = minVisionPerMin;
            this.maxVisionPerMin = maxVisionPerMin;
            this.maxKills = maxKills;
            this.laneMinionsAt10 = laneMinionsAt10;
            this.maxHealShieldPerMin = maxHealShieldPerMin;
            this.wardsPerMin = wardsPerMin;
            this.maxCcPerMin = maxCcPerMin;
        }

        static RoleProfile forSlot(int slot) {
            return values()[slot % 5];
        }
    }
}
