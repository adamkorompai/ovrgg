package gg.ovr.stats.performance;

import gg.ovr.common.kafka.Topics;
import gg.ovr.common.match.MatchInfo;
import gg.ovr.common.match.MatchMetadata;
import gg.ovr.common.match.MatchPerformance;
import gg.ovr.common.match.Participant;
import gg.ovr.common.match.RiotMatch;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The real application (application.yml, serdes, topology) against a real broker.
 */
@SpringBootTest
@Testcontainers
class MatchPerformanceKafkaIntegrationTest {

    @Container
    @ServiceConnection
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.1.0")
            .withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "false");

    /** Runs after the broker starts and before the Spring context, like kafka-init in docker-compose. */
    @BeforeAll
    static void createTopics() throws Exception {
        try (Admin admin = Admin.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers()))) {
            admin.createTopics(List.of(
                    new NewTopic(Topics.MATCH_RAW, 3, (short) 1),
                    new NewTopic(Topics.MATCH_PERFORMANCE, 3, (short) 1))).all().get();
        }
    }

    @Test
    void matchBecomesTenPerformancesKeyedByPuuid() throws Exception {
        RiotMatch match = match("EUW1_IT_1");

        try (KafkaProducer<String, Object> producer = new KafkaProducer<>(
                Map.of(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers()),
                new StringSerializer(), new JsonSerializer<>())) {
            // Unusable records first, on every partition: the stream must skip them, not stop.
            for (int partition = 0; partition < 3; partition++) {
                producer.send(new ProducerRecord<>(Topics.MATCH_RAW, partition, "EUW1_null", null)).get();
                producer.send(new ProducerRecord<>(Topics.MATCH_RAW, partition, "EUW1_no_metadata",
                        Map.of("info", match.info()))).get();
            }
            producer.send(new ProducerRecord<>(Topics.MATCH_RAW, "EUW1_IT_1", match)).get();
        }

        List<ConsumerRecord<String, MatchPerformance>> records = consume(Topics.MATCH_PERFORMANCE, 10);
        assertThat(records).hasSize(10).allSatisfy(record -> {
            assertThat(record.key()).isEqualTo(record.value().puuid());
            assertThat(record.value().matchId()).isEqualTo("EUW1_IT_1");
        });
    }

    private static List<ConsumerRecord<String, MatchPerformance>> consume(String topic, int expected) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "it-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        try (KafkaConsumer<String, MatchPerformance> consumer = new KafkaConsumer<>(config,
                new StringDeserializer(), new JsonDeserializer<>(MatchPerformance.class, false))) {
            consumer.subscribe(List.of(topic));
            List<ConsumerRecord<String, MatchPerformance>> records = new ArrayList<>();
            await().atMost(Duration.ofSeconds(60)).until(() -> {
                consumer.poll(Duration.ofMillis(500)).forEach(records::add);
                return records.size() >= expected;
            });
            return records;
        }
    }

    private static RiotMatch match(String matchId) {
        List<Participant> participants = IntStream.range(0, 10)
                .mapToObj(slot -> {
                    TestParticipant participant = new TestParticipant();
                    participant.puuid = matchId + "-puuid-" + slot;
                    return participant.build();
                })
                .toList();
        MatchInfo info = new MatchInfo(1, 1_767_225_600_000L, 1800, 420, "CLASSIC", "EUW1", participants);
        return new RiotMatch(new MatchMetadata(matchId, participants.stream().map(Participant::puuid).toList()), info);
    }
}
