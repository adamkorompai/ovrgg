package gg.ovr.api.player;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
class LookupController {

    private final LookupService lookups;

    LookupController(LookupService lookups) {
        this.lookups = lookups;
    }

    /**
     * 202: the card is computed asynchronously, follow it on {@code /api/cards/{puuid}/stream}.
     */
    @PostMapping("/api/lookups")
    ResponseEntity<LookupResponse> requestLookup(@RequestBody LookupRequest request) {
        RiotId riotId = new RiotId(request.gameName(), request.tagLine());
        return lookups.requestLookup(riotId)
                .map(puuid -> ResponseEntity.accepted().body(new LookupResponse(puuid)))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No account with this Riot ID"));
    }

    @ExceptionHandler(InvalidRiotIdException.class)
    ProblemDetail invalidRiotId(InvalidRiotIdException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    record LookupRequest(String gameName, String tagLine) {
    }

    record LookupResponse(String puuid) {
    }
}
