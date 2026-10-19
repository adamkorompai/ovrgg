package gg.ovr.card.player;

import gg.ovr.card.PostgresTestConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PostgresTestConfig.class, RequestedPlayerRepository.class})
class RequestedPlayerRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-10-19T12:00:00Z");

    @Autowired
    private RequestedPlayerRepository repository;

    @Test
    void unknownPlayerIsNotRequested() {
        assertThat(repository.findByPuuid("puuid-1")).isEmpty();
    }

    @Test
    void savedPlayerIsFound() {
        repository.save(new RequestedPlayer("puuid-1", "Alice", "EUW"), NOW);

        assertThat(repository.findByPuuid("puuid-1")).contains(new RequestedPlayer("puuid-1", "Alice", "EUW"));
    }

    @Test
    void newLookupUpdatesRiotId() {
        repository.save(new RequestedPlayer("puuid-1", "Alice", "EUW"), NOW);
        repository.save(new RequestedPlayer("puuid-1", "AliceRenamed", "EUW2"), NOW.plusSeconds(60));

        assertThat(repository.findByPuuid("puuid-1")).contains(new RequestedPlayer("puuid-1", "AliceRenamed", "EUW2"));
    }
}
