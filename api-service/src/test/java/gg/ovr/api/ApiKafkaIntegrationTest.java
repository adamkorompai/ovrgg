package gg.ovr.api;

import com.jayway.jsonpath.JsonPath;
import gg.ovr.common.card.CardUpdated;
import gg.ovr.common.kafka.Topics;
import gg.ovr.common.player.PlayerLookupRequested;
import gg.ovr.common.rating.Attributes;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.test.web.servlet.MockMvc;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The real application (application.yml, producer, card.updated listener) against a real broker.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ApiKafkaIntegrationTest {

    @Container
    @ServiceConnection
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.1.0")
            .withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "false");

    @Autowired
    private MockMvc mvc;

    /** Runs after the broker starts and before the Spring context, like kafka-init in docker-compose. */
    @BeforeAll
    static void createTopics() throws Exception {
        try (Admin admin = Admin.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers()))) {
            admin.createTopics(List.of(
                    new NewTopic(Topics.PLAYER_LOOKUP_REQUESTED, 3, (short) 1),
                    new NewTopic(Topics.CARD_UPDATED, 3, (short) 1)
                            .configs(Map.of("cleanup.policy", "compact")))).all().get();
        }
    }

    @Test
    void lookupIsPublishedThenTheCardIsServed() throws Exception {
        String response = mvc.perform(post("/api/lookups").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"gameName": "Alice", "tagLine": "EUW"}"""))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        String puuid = JsonPath.read(response, "$.puuid");

        List<ConsumerRecord<String, PlayerLookupRequested>> lookups = consumeLookups(1);
        assertThat(lookups).singleElement().satisfies(record -> {
            assertThat(record.key()).isEqualTo(puuid);
            assertThat(record.value().gameName()).isEqualTo("Alice");
        });

        CardUpdated card = new CardUpdated(puuid, "Alice", "EUW", 64, new Attributes(60, 61, 62, 63, 64, 65),
                12, false, List.of(7.0), Instant.parse("2026-10-19T12:00:00Z"));
        try (KafkaProducer<String, CardUpdated> producer = new KafkaProducer<>(
                Map.of(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers()),
                new StringSerializer(), new JsonSerializer<>())) {
            producer.send(new ProducerRecord<>(Topics.CARD_UPDATED, puuid, card)).get();
        }

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> mvc.perform(get("/api/cards/" + puuid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ovr").value(64))
                .andExpect(jsonPath("$.updatedAt").value("2026-10-19T12:00:00Z")));
    }

    private static List<ConsumerRecord<String, PlayerLookupRequested>> consumeLookups(int expected) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "it-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        try (KafkaConsumer<String, PlayerLookupRequested> consumer = new KafkaConsumer<>(config,
                new StringDeserializer(), new JsonDeserializer<>(PlayerLookupRequested.class, false))) {
            consumer.subscribe(List.of(Topics.PLAYER_LOOKUP_REQUESTED));
            List<ConsumerRecord<String, PlayerLookupRequested>> records = new ArrayList<>();
            await().atMost(Duration.ofSeconds(60)).until(() -> {
                consumer.poll(Duration.ofMillis(500)).forEach(records::add);
                return records.size() >= expected;
            });
            return records;
        }
    }
}
