package gg.ovr.card.rating;

import gg.ovr.card.performance.PerformanceRepository;
import gg.ovr.card.player.RequestedPlayer;
import gg.ovr.card.player.RequestedPlayerRepository;
import gg.ovr.common.match.MatchPerformance;
import gg.ovr.common.player.PlayerLookupRequested;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * Stores every performance, publishes cards only for looked-up players. Lookups and performances
 * come from two topics with no ordering between them, so both paths may publish the card. Both run
 * under a per-player lock, publishing before commit: the last card published is always the latest.
 * <p>
 * If the commit fails after the send, the topic briefly holds a card the database does not back.
 * The exception reaches the listener, the offset is not committed and the message is redelivered:
 * the retry stores the data again and republishes the same card. Publishing after commit would
 * release the lock first and let two threads publish out of order (see CardServiceRollbackTest).
 */
@Service
public class CardService {

    private final PerformanceRepository performances;
    private final RequestedPlayerRepository requestedPlayers;
    private final CardCalculator calculator;
    private final CardPublisher publisher;
    private final Clock clock;

    public CardService(PerformanceRepository performances, RequestedPlayerRepository requestedPlayers,
                       CardCalculator calculator, CardPublisher publisher, Clock clock) {
        this.performances = performances;
        this.requestedPlayers = requestedPlayers;
        this.calculator = calculator;
        this.publisher = publisher;
        this.clock = clock;
    }

    @Transactional
    public void onLookup(PlayerLookupRequested lookup) {
        requestedPlayers.lockPlayer(lookup.puuid());
        RequestedPlayer player = new RequestedPlayer(lookup.puuid(), lookup.gameName(), lookup.tagLine());
        requestedPlayers.save(player, lookup.requestedAt());
        publishCard(player);
    }

    /**
     * A duplicate performance is not stored twice, but the card is still republished: if the previous
     * attempt crashed after storing and before publishing, this is what brings the card up to date.
     */
    @Transactional
    public void onPerformance(MatchPerformance performance) {
        requestedPlayers.lockPlayer(performance.puuid());
        performances.save(performance);
        requestedPlayers.findByPuuid(performance.puuid()).ifPresent(this::publishCard);
    }

    private void publishCard(RequestedPlayer player) {
        calculator.compute(player, performances.findByPuuid(player.puuid()), clock.instant())
                .ifPresent(publisher::publish);
    }
}
