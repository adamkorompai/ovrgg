package gg.ovr.card.player;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

@Repository
public class RequestedPlayerRepository {

    private final JdbcClient jdbc;

    public RequestedPlayerRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Upsert: a new lookup refreshes the Riot ID, which the player may have changed.
     */
    public void save(RequestedPlayer player, Instant requestedAt) {
        jdbc.sql("""
                        INSERT INTO requested_player (puuid, game_name, tag_line, requested_at)
                        VALUES (?, ?, ?, ?)
                        ON CONFLICT (puuid) DO UPDATE
                        SET game_name = EXCLUDED.game_name, tag_line = EXCLUDED.tag_line,
                            requested_at = EXCLUDED.requested_at
                        """)
                .params(player.puuid(), player.gameName(), player.tagLine(), Timestamp.from(requestedAt))
                .update();
    }

    /**
     * Serializes the work on one player until the current transaction ends: a lookup and a performance
     * of the same puuid, consumed on two threads, must not compute and publish the card concurrently.
     * Advisory lock rather than a row lock, because the requested_player row may not exist yet.
     */
    public void lockPlayer(String puuid) {
        jdbc.sql("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))").param(puuid).query().listOfRows();
    }

    public Optional<RequestedPlayer> findByPuuid(String puuid) {
        return jdbc.sql("SELECT puuid, game_name, tag_line FROM requested_player WHERE puuid = ?")
                .param(puuid)
                .query((rs, rowNum) -> new RequestedPlayer(
                        rs.getString("puuid"), rs.getString("game_name"), rs.getString("tag_line")))
                .optional();
    }
}
