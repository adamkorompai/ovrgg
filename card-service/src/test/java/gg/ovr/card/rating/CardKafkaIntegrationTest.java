package gg.ovr.card.rating;

import gg.ovr.card.PostgresTestConfig;
import gg.ovr.common.card.CardUpdated;
import gg.ovr.common.kafka.Topics;
import gg.ovr.common.match.MatchPerformance;
import gg.ovr.common.player.PlayerLookupRequested;
import gg.ovr.common.rating.Attributes;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
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
import org.springframework.context.annotation.Import;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The real application (application.yml, both listeners, producer, Postgres) against a real broker.
 */
@SpringBootTest
@Testcontainers
@Import(PostgresTestConfig.class)
class CardKafkaIntegrationTest {

    @Container
    @ServiceConnection
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.1.0")
            .withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "false");

    /** Runs after the broker starts and before the Spring context, like kafka-init in docker-compose. */
    @BeforeAll
    static void createTopics() throws Exception {
        try (Admin admin = Admin.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers()))) {
            admin.createTopics(List.of(
                    new NewTopic(Topics.PLAYER_LOOKUP_REQUESTED, 3, (short) 1),
                    new NewTopic(Topics.MATCH_PERFORMANCE, 3, (short) 1),
                    new NewTopic(Topics.CARD_UPDATED, 3, (short) 1)
                            .configs(Map.of("cleanup.policy", "compact")))).all().get();
        }
    }

    @Test
    void lookupAndPerformancesProduceTheCard() throws Exception {
        try (KafkaProducer<String, Object> producer = new KafkaProducer<>(
                Map.of(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers()),
                new StringSerializer(), new JsonSerializer<>())) {
            producer.send(new ProducerRecord<>(Topics.PLAYER_LOOKUP_REQUESTED, "puuid-it",
                    new PlayerLookupRequested("puuid-it", "Alice", "EUW", Instant.now()))).get();
            producer.send(new ProducerRecord<>(Topics.MATCH_PERFORMANCE, "puuid-it", perf("EUW1_1"))).get();
            producer.send(new ProducerRecord<>(Topics.MATCH_PERFORMANCE, "puuid-it", perf("EUW1_2"))).get();
        }

        CardUpdated card = awaitCard("puuid-it", 2);
        assertThat(card.gameName()).isEqualTo("Alice");
        assertThat(card.ovr()).isEqualTo(60);
    }

    /** Cards arrive progressively: waits for the one built from {@code matchesCounted} matches. */
    private static CardUpdated awaitCard(String puuid, int matchesCounted) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "it-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        try (KafkaConsumer<String, CardUpdated> consumer = new KafkaConsumer<>(config,
                new StringDeserializer(), new JsonDeserializer<>(CardUpdated.class, false))) {
            consumer.subscribe(List.of(Topics.CARD_UPDATED));
            AtomicReference<CardUpdated> found = new AtomicReference<>();
            await().atMost(Duration.ofSeconds(60)).until(() -> {
                consumer.poll(Duration.ofMillis(500)).forEach(record -> {
                    if (record.key().equals(puuid) && record.value().matchesCounted() == matchesCounted) {
                        found.set(record.value());
                    }
                });
                return found.get() != null;
            });
            return found.get();
        }
    }

    private static MatchPerformance perf(String matchId) {
        return new MatchPerformance(matchId, "puuid-it", 420, 1_767_225_600_000L, 1800, "MIDDLE", true,
                3, 2, 7, new Attributes(60, 60, 60, 60, 60, 60), 60, 7.2);
    }
}
