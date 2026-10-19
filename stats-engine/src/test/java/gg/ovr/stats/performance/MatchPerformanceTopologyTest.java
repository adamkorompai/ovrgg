package gg.ovr.stats.performance;

import gg.ovr.common.kafka.Topics;
import gg.ovr.common.match.MatchInfo;
import gg.ovr.common.match.MatchMetadata;
import gg.ovr.common.match.MatchPerformance;
import gg.ovr.common.match.Participant;
import gg.ovr.common.match.RiotMatch;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.TopologyTestDriver;
import org.apache.kafka.streams.errors.LogAndContinueExceptionHandler;
import org.apache.kafka.streams.errors.LogAndContinueProcessingExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.List;
import java.util.Properties;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class MatchPerformanceTopologyTest {

    private TopologyTestDriver driver;
    private TestInputTopic<String, RiotMatch> matches;
    private TestInputTopic<String, String> rawBytes;
    private TestOutputTopic<String, MatchPerformance> performances;

    @BeforeEach
    void setUp() {
        StreamsBuilder builder = new StreamsBuilder();
        new MatchPerformanceTopology(new ProvisionalPerformanceCalculator()).build(builder);

        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "stats-engine-test");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:9092");
        // Same handler as application.yml.
        props.put(StreamsConfig.DEFAULT_DESERIALIZATION_EXCEPTION_HANDLER_CLASS_CONFIG,
                LogAndContinueExceptionHandler.class);
        props.put(StreamsConfig.PROCESSING_EXCEPTION_HANDLER_CLASS_CONFIG,
                LogAndContinueProcessingExceptionHandler.class);
        driver = new TopologyTestDriver(builder.build(), props);

        // Like a real producer: JsonSerializer adds a __TypeId__ header by default.
        matches = driver.createInputTopic(Topics.MATCH_RAW, new StringSerializer(), new JsonSerializer<>());
        rawBytes = driver.createInputTopic(Topics.MATCH_RAW, new StringSerializer(), new StringSerializer());
        performances = driver.createOutputTopic(Topics.MATCH_PERFORMANCE, new StringDeserializer(),
                new JsonDeserializer<>(MatchPerformance.class, false));
    }

    @AfterEach
    void tearDown() {
        driver.close();
    }

    @Test
    void emitsOnePerformancePerParticipantKeyedByPuuid() {
        RiotMatch match = match("EUW1_1");

        matches.pipeInput("EUW1_1", match);

        List<KeyValue<String, MatchPerformance>> output = performances.readKeyValuesToList();
        assertThat(output).hasSize(10);
        assertThat(output).allSatisfy(record -> {
            assertThat(record.key).isEqualTo(record.value.puuid());
            assertThat(record.value.matchId()).isEqualTo("EUW1_1");
        });
        assertThat(output).extracting(record -> record.key).containsExactlyElementsOf(match.metadata().participants());
    }

    @Test
    void skipsUnreadableMessageAndKeepsProcessing() {
        rawBytes.pipeInput("EUW1_bad", "{not json");
        matches.pipeInput("EUW1_2", match("EUW1_2"));

        assertThat(performances.readValuesToList())
                .hasSize(10)
                .allSatisfy(performance -> assertThat(performance.matchId()).isEqualTo("EUW1_2"));
    }

    @Test
    void skipsNullValueAndKeepsProcessing() {
        matches.pipeInput("EUW1_tombstone", null);
        matches.pipeInput("EUW1_3", match("EUW1_3"));

        assertThat(performances.readValuesToList())
                .hasSize(10)
                .allSatisfy(performance -> assertThat(performance.matchId()).isEqualTo("EUW1_3"));
    }

    @Test
    void skipsMatchThatCannotBeRatedAndKeepsProcessing() {
        rawBytes.pipeInput("EUW1_no_metadata", """
                {"info": {"gameId": 1, "gameCreation": 0, "gameDuration": 1800, "queueId": 420,
                          "gameMode": "CLASSIC", "platformId": "EUW1", "participants": []}}""");
        matches.pipeInput("EUW1_4", match("EUW1_4"));

        assertThat(performances.readValuesToList())
                .hasSize(10)
                .allSatisfy(performance -> assertThat(performance.matchId()).isEqualTo("EUW1_4"));
    }

    private static RiotMatch match(String matchId) {
        List<Participant> participants = IntStream.range(0, 10)
                .mapToObj(slot -> {
                    TestParticipant participant = new TestParticipant();
                    participant.puuid = matchId + "-puuid-" + slot;
                    participant.win = slot < 5;
                    return participant.build();
                })
                .toList();
        MatchInfo info = new MatchInfo(1, 1_767_225_600_000L, 1800, 420, "CLASSIC", "EUW1", participants);
        return new RiotMatch(new MatchMetadata(matchId, participants.stream().map(Participant::puuid).toList()), info);
    }
}
