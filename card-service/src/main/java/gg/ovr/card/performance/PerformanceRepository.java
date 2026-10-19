package gg.ovr.card.performance;

import gg.ovr.common.match.MatchPerformance;
import gg.ovr.common.rating.Attributes;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class PerformanceRepository {

    private static final RowMapper<MatchPerformance> ROW_MAPPER = (rs, rowNum) -> new MatchPerformance(
            rs.getString("match_id"),
            rs.getString("puuid"),
            rs.getInt("queue_id"),
            rs.getLong("game_creation"),
            rs.getLong("duration_seconds"),
            rs.getString("team_position"),
            rs.getBoolean("win"),
            rs.getInt("kills"),
            rs.getInt("deaths"),
            rs.getInt("assists"),
            new Attributes(rs.getDouble("agr"), rs.getDouble("frm"), rs.getDouble("vis"),
                    rs.getDouble("tea"), rs.getDouble("sur"), rs.getDouble("imp")),
            rs.getDouble("match_score"),
            rs.getDouble("match_rating"));

    private final JdbcClient jdbc;

    public PerformanceRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Idempotent: a (match, player) pair already stored is left untouched (Kafka delivers at least once).
     */
    public void save(MatchPerformance p) {
        Attributes a = p.attributes();
        jdbc.sql("""
                        INSERT INTO match_performance (match_id, puuid, queue_id, game_creation, duration_seconds,
                            team_position, win, kills, deaths, assists, agr, frm, vis, tea, sur, imp,
                            match_score, match_rating)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT (match_id, puuid) DO NOTHING
                        """)
                .params(p.matchId(), p.puuid(), p.queueId(), p.gameCreation(), p.durationSeconds(),
                        p.teamPosition(), p.win(), p.kills(), p.deaths(), p.assists(),
                        a.agr(), a.frm(), a.vis(), a.tea(), a.sur(), a.imp(), p.matchScore(), p.matchRating())
                .update();
    }

    /**
     * @return every stored performance of the player, most recent first
     */
    public List<MatchPerformance> findByPuuid(String puuid) {
        return jdbc.sql("SELECT * FROM match_performance WHERE puuid = ? ORDER BY game_creation DESC, match_id")
                .param(puuid)
                .query(ROW_MAPPER)
                .list();
    }
}
