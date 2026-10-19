package gg.ovr.api.player;

import java.util.regex.Pattern;

/**
 * A Riot ID ({@code gameName#tagLine}) as typed by a user, trimmed and validated with Riot's rules:
 * game name 3–16 characters (any script), tag 3–5 letters or digits.
 */
public record RiotId(String gameName, String tagLine) {

    private static final Pattern TAG_LINE = Pattern.compile("[\\p{L}\\p{N}]{3,5}");

    public RiotId {
        if (gameName == null || tagLine == null) {
            throw new InvalidRiotIdException("gameName and tagLine are required");
        }
        gameName = gameName.strip();
        tagLine = tagLine.strip();
        int length = gameName.codePointCount(0, gameName.length());
        if (length < 3 || length > 16) {
            throw new InvalidRiotIdException("gameName must be 3 to 16 characters");
        }
        if (!TAG_LINE.matcher(tagLine).matches()) {
            throw new InvalidRiotIdException("tagLine must be 3 to 5 letters or digits");
        }
    }
}
