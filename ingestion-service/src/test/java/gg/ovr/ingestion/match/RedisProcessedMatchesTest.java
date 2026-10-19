package gg.ovr.ingestion.match;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.redis.DataRedisTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@DataRedisTest
@Testcontainers
class RedisProcessedMatchesTest {

    private static final Duration TTL = Duration.ofDays(30);

    @Container
    @ServiceConnection
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @Autowired
    private StringRedisTemplate redis;

    private RedisProcessedMatches processed;

    @BeforeEach
    void setUp() {
        redis.getConnectionFactory().getConnection().serverCommands().flushAll();
        processed = new RedisProcessedMatches(redis, TTL);
    }

    @Test
    void unknownMatchIsNotProcessed() {
        assertThat(processed.isProcessed("EUW1_1")).isFalse();
    }

    @Test
    void markedMatchIsProcessed() {
        processed.markProcessed("EUW1_1");

        assertThat(processed.isProcessed("EUW1_1")).isTrue();
        assertThat(processed.isProcessed("EUW1_2")).isFalse();
    }

    @Test
    void markExpiresAfterTtl() {
        processed.markProcessed("EUW1_1");

        Long ttlSeconds = redis.getExpire("ovr:match:processed:EUW1_1");
        assertThat(ttlSeconds).isBetween(TTL.toSeconds() - 5, TTL.toSeconds());
    }
}
