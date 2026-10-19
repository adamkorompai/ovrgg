package gg.ovr.stats.performance;

import gg.ovr.common.kafka.Topics;
import gg.ovr.common.match.MatchPerformance;
import gg.ovr.common.match.RiotMatch;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Produced;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.support.serializer.JsonSerde;
import org.springframework.stereotype.Component;

/**
 * {@code match.raw} (key matchId) → {@code match.performance} (key puuid): one match in, ten performances out.
 * Stateless: a reprocessed match yields the same performances, and card-service ignores duplicates.
 * A match that cannot be read or rated is logged and skipped (application.yml), it never stops the stream.
 */
@Component
public class MatchPerformanceTopology {

    // Reading: ignore the producer's __TypeId__ header, the target type is known.
    private static final Serde<RiotMatch> MATCH_SERDE = new JsonSerde<>(RiotMatch.class).ignoreTypeHeaders();
    // Writing: no type header, consumers declare the type they expect.
    private static final Serde<MatchPerformance> PERFORMANCE_SERDE = new JsonSerde<>(MatchPerformance.class).noTypeInfo();

    private final PerformanceCalculator calculator;

    public MatchPerformanceTopology(PerformanceCalculator calculator) {
        this.calculator = calculator;
    }

    @Autowired
    void build(StreamsBuilder builder) {
        builder.stream(Topics.MATCH_RAW, Consumed.with(Serdes.String(), MATCH_SERDE))
                // A null value (tombstone, or sent by hand) carries no match.
                .filter((matchId, match) -> match != null)
                // flatMap, not flatMapValues: the key changes from matchId to puuid.
                .flatMap((matchId, match) -> calculator.compute(match).stream()
                        .map(performance -> KeyValue.pair(performance.puuid(), performance))
                        .toList())
                .to(Topics.MATCH_PERFORMANCE, Produced.with(Serdes.String(), PERFORMANCE_SERDE));
    }
}
