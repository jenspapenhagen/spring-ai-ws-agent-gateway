package de.papenhagen.gateway.decorator;

import de.papenhagen.gateway.domain.GatewaySession;
import de.papenhagen.gateway.port.GatewaySessionRepository;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

public class RedisGatewaySessionRepository implements GatewaySessionRepository {

    private static final Logger log = LoggerFactory.getLogger(RedisGatewaySessionRepository.class);
    private static final String KEY_PREFIX = "gateway:session:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Duration sessionTtl;

    public RedisGatewaySessionRepository(
        final StringRedisTemplate template,
        final ObjectMapper mapper,
        final Duration ttl
    ) {
        this.redis = template;
        this.objectMapper = mapper;
        this.sessionTtl = ttl;
    }

    @Override
    public GatewaySession getOrCreate(final String sessionId) {
        final GatewaySession session = new GatewaySession();
        final String key = KEY_PREFIX + sessionId;

        final String json = redis.opsForValue().get(key);
        if (json != null) {
            try {
                final RedisSession persisted = objectMapper.readValue(json, RedisSession.class);
                persisted.applyTo(session);
                log.debug("Loaded session from Redis: sessionId={}", sessionId);
            } catch (Exception e) {
                log.warn("Failed to deserialize session, starting fresh: sessionId={}", sessionId, e);
            }
        }

        redis.opsForValue().set(key, objectMapper.writeValueAsString(new RedisSession(session)), sessionTtl);
        log.debug("Persisted session to Redis: sessionId={}", sessionId);

        return session;
    }

    @Override
    public GatewaySession remove(final String sessionId) {
        final String key = KEY_PREFIX + sessionId;
        final GatewaySession removed = new GatewaySession();

        final String json = redis.opsForValue().get(key);
        if (json != null) {
            try {
                final RedisSession persisted = objectMapper.readValue(json, RedisSession.class);
                persisted.applyTo(removed);
            } catch (Exception e) {
                log.warn("Failed to deserialize session before removal: sessionId={}", sessionId, e);
            }
        }

        redis.delete(key);
        log.info("Removed session from Redis: sessionId={}", sessionId);
        return removed;
    }
}
