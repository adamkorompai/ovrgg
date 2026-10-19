package gg.ovr.card.player;

/**
 * A player someone looked up on the site: only they get a card.
 */
public record RequestedPlayer(String puuid, String gameName, String tagLine) {
}
