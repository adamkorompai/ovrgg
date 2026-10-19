package gg.ovr.ingestion.lookup;

import gg.ovr.common.match.RiotMatch;
import gg.ovr.common.player.PlayerLookupRequested;
import gg.ovr.ingestion.match.FakeMatchSource;
import gg.ovr.ingestion.match.MatchPublisher;
import gg.ovr.ingestion.match.MatchSource;
import gg.ovr.ingestion.match.ProcessedMatches;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LookupHandlerTest {

    private static final String PUUID = "puuid-alice";
    private static final PlayerLookupRequested LOOKUP =
            new PlayerLookupRequested(PUUID, "Alice", "EUW", Instant.parse("2026-10-19T12:00:00Z"));

    private final FakeMatchSource fakeSource = new FakeMatchSource();
    private final InMemoryProcessedMatches processed = new InMemoryProcessedMatches();
    private final RecordingPublisher publisher = new RecordingPublisher();

    @Test
    void publishesRecentMatchesAndMarksThemProcessed() {
        List<String> ids = fakeSource.findRecentMatchIds(PUUID, 3);

        handler(fakeSource, 3).handle(LOOKUP);

        assertThat(publisher.publishedIds()).containsExactlyElementsOf(ids);
        assertThat(processed.ids).containsExactlyInAnyOrderElementsOf(ids);
    }

    @Test
    void skipsMatchesAlreadyProcessed() {
        List<String> ids = fakeSource.findRecentMatchIds(PUUID, 3);
        processed.markProcessed(ids.get(1));

        handler(fakeSource, 3).handle(LOOKUP);

        assertThat(publisher.publishedIds()).containsExactly(ids.get(0), ids.get(2));
    }

    @Test
    void secondLookupPublishesNothing() {
        LookupHandler handler = handler(fakeSource, 3);
        handler.handle(LOOKUP);
        publisher.published.clear();

        handler.handle(LOOKUP);

        assertThat(publisher.published).isEmpty();
    }

    @Test
    void missingMatchIsSkippedAndNotMarked() {
        MatchSource withMissing = new MatchSource() {
            @Override
            public List<String> findRecentMatchIds(String puuid, int count) {
                return List.of("GONE_1", fakeSource.findRecentMatchIds(puuid, 1).getFirst());
            }

            @Override
            public Optional<RiotMatch> findMatch(String matchId) {
                return fakeSource.findMatch(matchId);
            }
        };

        handler(withMissing, 2).handle(LOOKUP);

        assertThat(publisher.published).hasSize(1);
        assertThat(processed.ids).doesNotContain("GONE_1").hasSize(1);
    }

    @Test
    void failedPublishIsNotMarkedAndStopsTheLookup() {
        List<String> ids = fakeSource.findRecentMatchIds(PUUID, 3);
        publisher.failOn = ids.get(1);

        assertThatThrownBy(() -> handler(fakeSource, 3).handle(LOOKUP)).hasMessageContaining("broker down");

        assertThat(processed.ids).containsExactly(ids.get(0));
        assertThat(publisher.publishedIds()).containsExactly(ids.get(0));
    }

    private LookupHandler handler(MatchSource source, int matchCount) {
        return new LookupHandler(source, processed, publisher, matchCount);
    }

    private static final class InMemoryProcessedMatches implements ProcessedMatches {
        private final Set<String> ids = new HashSet<>();

        @Override
        public boolean isProcessed(String matchId) {
            return ids.contains(matchId);
        }

        @Override
        public void markProcessed(String matchId) {
            ids.add(matchId);
        }
    }

    private static final class RecordingPublisher implements MatchPublisher {
        private final List<RiotMatch> published = new ArrayList<>();
        private String failOn;

        @Override
        public void publish(RiotMatch match) {
            if (match.metadata().matchId().equals(failOn)) {
                throw new IllegalStateException("broker down");
            }
            published.add(match);
        }

        List<String> publishedIds() {
            return published.stream().map(match -> match.metadata().matchId()).toList();
        }
    }
}
