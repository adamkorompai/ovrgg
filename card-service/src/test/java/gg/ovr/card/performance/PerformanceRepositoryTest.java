package gg.ovr.card.performance;

import gg.ovr.card.PostgresTestConfig;
import gg.ovr.common.match.MatchPerformance;
import gg.ovr.common.rating.Attributes;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PostgresTestConfig.class, PerformanceRepository.class})
class PerformanceRepositoryTest {

    @Autowired
    private PerformanceRepository repository;

    @Test
    void savedPerformanceIsFoundByPuuid() {
        MatchPerformance performance = perf("EUW1_1", "puuid-1");

        repository.save(performance);

        assertThat(repository.findByPuuid("puuid-1")).containsExactly(performance);
    }

    @Test
    void duplicateIsIgnored() {
        repository.save(perf("EUW1_1", "puuid-1"));
        repository.save(perf("EUW1_1", "puuid-1"));

        assertThat(repository.findByPuuid("puuid-1")).hasSize(1);
    }

    @Test
    void sameMatchStoresOneRowPerPlayer() {
        repository.save(perf("EUW1_1", "puuid-1"));
        repository.save(perf("EUW1_1", "puuid-2"));

        assertThat(repository.findByPuuid("puuid-1")).extracting(MatchPerformance::puuid).containsExactly("puuid-1");
        assertThat(repository.findByPuuid("puuid-2")).extracting(MatchPerformance::puuid).containsExactly("puuid-2");
    }

    @Test
    void keepsEmptyTeamPosition() {
        MatchPerformance aram = new MatchPerformance("EUW1_2", "puuid-1", 450, 1L, 1200, "", false,
                0, 0, 0, new Attributes(0, 0, 0, 0, 0, 0), 0, 3.0);

        repository.save(aram);

        assertThat(repository.findByPuuid("puuid-1")).containsExactly(aram);
    }

    private static MatchPerformance perf(String matchId, String puuid) {
        return new MatchPerformance(matchId, puuid, 420, 1_767_225_600_000L, 1834, "UTILITY", true,
                2, 5, 11, new Attributes(12.5, 7.6, 71.3, 66.6, 70.6, 28.7), 42.88, 6.0);
    }
}
