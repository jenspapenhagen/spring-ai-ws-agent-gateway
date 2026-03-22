package de.papenhagen.gateway.decorator;

import de.papenhagen.gateway.domain.GatewaySession;
import de.papenhagen.gateway.port.GatewaySessionRepository;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisGatewaySessionRepositoryTest {

    private StringRedisTemplate redis;
    private ValueOperations<String, String> ops;
    private ObjectMapper objectMapper;
    private GatewaySessionRepository repo;

    @BeforeEach
    void setUp() {
        redis = mock(StringRedisTemplate.class);
        ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        objectMapper = new ObjectMapper();
        repo = new RedisGatewaySessionRepository(redis, objectMapper, Duration.ofMinutes(30));
    }

    @Test
    void givenNewSession_whenGetOrCreate_thenCreateAndPersist() {
        when(ops.get("gateway:session:s1")).thenReturn(null);

        GatewaySession session = repo.getOrCreate("s1");

        assertThat(session.history()).isEmpty();
        assertThat(session.responses()).isEmpty();
        verify(ops).set(eq("gateway:session:s1"), any(String.class), eq(Duration.ofMinutes(30)));
    }

    @Test
    void givenExistingSession_whenGetOrCreate_thenLoadFromRedis() {
        final String json = """
            {
              "history":["hello"],
              "responseOrder":["r1"],
              "responses":{"r1":{"id":"r1","previousId":null,"input":["hi"],"output":"hi back","status":"completed"}},
              "lastResponseId":"r1"
            }
            """;
        when(ops.get("gateway:session:s1")).thenReturn(json);

        GatewaySession session = repo.getOrCreate("s1");

        assertThat(session.history()).containsExactly("hello");
        assertThat(session.lastResponseId()).isEqualTo("r1");
        assertThat(session.responses()).containsKey("r1");
    }

    @Test
    void givenCorruptJson_whenGetOrCreate_thenStartFreshSession() {
        when(ops.get("gateway:session:s1")).thenReturn("not valid json {{{");

        GatewaySession session = repo.getOrCreate("s1");

        assertThat(session.history()).isEmpty();
        assertThat(session.responses()).isEmpty();
        verify(ops).set(eq("gateway:session:s1"), any(String.class), any(Duration.class));
    }

    @Test
    void givenExistingSession_whenRemove_thenDeleteKeyAndReturnSession() {
        final String json = """
            {
              "history":["hello"],
              "responseOrder":[],
              "responses":{},
              "lastResponseId":null
            }
            """;
        when(ops.get("gateway:session:s1")).thenReturn(json);

        GatewaySession removed = repo.remove("s1");

        assertThat(removed.history()).containsExactly("hello");
        verify(redis).delete("gateway:session:s1");
    }

    @Test
    void givenModifiedSession_whenGetOrCreate_thenPersistCurrentState() {
        when(ops.get("gateway:session:s1")).thenReturn(null);

        GatewaySession session = repo.getOrCreate("s1");
        session.history().add("msg1");
        session.setLastResponseId("r1");

        repo.getOrCreate("s1");

        verify(ops, org.mockito.Mockito.times(2))
            .set(eq("gateway:session:s1"), any(String.class), eq(Duration.ofMinutes(30)));
    }
}
