package gg.ovr.api.player;

public class InvalidRiotIdException extends IllegalArgumentException {

    public InvalidRiotIdException(String message) {
        super(message);
    }
}
