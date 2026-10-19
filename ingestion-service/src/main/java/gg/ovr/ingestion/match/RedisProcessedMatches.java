package gg.ovr.ingestion.match;

import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

/**
 * One key per match with a TTL: a single Redis set would grow forever, its members cannot expire.
 * An expired match is published again if it shows up, which downstream deduplication absorbs.
 */
public class RedisProcessedMatches implements ProcessedMatches {

    private static final String KEY_PREFIX = "ovr:match:processed:";

    private final StringRedisTemplate redis;
    private final Duration ttl;

    public RedisProcessedMatches(StringRedisTemplate redis, Duration ttl) {
        this.redis = redis;
        this.ttl = ttl;
    }

    @Override
    public boolean isProcessed(String matchId) {
        return Boolean.TRUE.equals(redis.hasKey(KEY_PREFIX + matchId));
    }

    @Override
    public void markProcessed(String matchId) {
        redis.opsForValue().set(KEY_PREFIX + matchId, "1", ttl);
    }
}
