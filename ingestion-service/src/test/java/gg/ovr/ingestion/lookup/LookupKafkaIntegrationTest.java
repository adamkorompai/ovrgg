package gg.ovr.ingestion.lookup;

import gg.ovr.common.kafka.Topics;
import gg.ovr.common.match.RiotMatch;
import gg.ovr.common.player.PlayerLookupRequested;
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
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The real application (application.yml, listener, producer, Redis) against a real broker.
 */
@SpringBootTest
@Testcontainers
class LookupKafkaIntegrationTest {

    @Container
    @ServiceConnection
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.1.0")
            .withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "false");

    @Container
    @ServiceConnection(name = "redis")
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    /** Runs after the broker starts and before the Spring context, like kafka-init in docker-compose. */
    @BeforeAll
    static void createTopics() throws Exception {
        try (Admin admin = Admin.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers()))) {
            admin.createTopics(List.of(
                    new NewTopic(Topics.PLAYER_LOOKUP_REQUESTED, 3, (short) 1),
                    new NewTopic(Topics.MATCH_RAW, 3, (short) 1))).all().get();
        }
    }

    @Test
    void lookupPublishesRecentMatchesKeyedByMatchId() throws Exception {
        PlayerLookupRequested lookup = new PlayerLookupRequested("puuid-it", "Alice", "EUW", Instant.now());

        try (KafkaProducer<String, PlayerLookupRequested> producer = new KafkaProducer<>(
                Map.of(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers()),
                new StringSerializer(), new JsonSerializer<>())) {
            producer.send(new ProducerRecord<>(Topics.PLAYER_LOOKUP_REQUESTED, lookup.puuid(), lookup)).get();
        }

        List<ConsumerRecord<String, RiotMatch>> records = consume(Topics.MATCH_RAW, 30);
        assertThat(records).hasSize(30).allSatisfy(record -> {
            assertThat(record.key()).isEqualTo(record.value().metadata().matchId());
            assertThat(record.value().metadata().participants()).contains("puuid-it");
        });
    }

    private static List<ConsumerRecord<String, RiotMatch>> consume(String topic, int expected) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "it-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        try (KafkaConsumer<String, RiotMatch> consumer = new KafkaConsumer<>(config,
                new StringDeserializer(), new JsonDeserializer<>(RiotMatch.class, false))) {
            consumer.subscribe(List.of(topic));
            List<ConsumerRecord<String, RiotMatch>> records = new ArrayList<>();
            await().atMost(Duration.ofSeconds(60)).until(() -> {
                consumer.poll(Duration.ofMillis(500)).forEach(records::add);
                return records.size() >= expected;
            });
            return records;
        }
    }
}
