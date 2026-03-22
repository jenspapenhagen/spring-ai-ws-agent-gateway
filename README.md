# Spring AI Open Responses WebSocket Gateway Starter

## Purpose

This repository packages the gateway as a Spring Boot starter so you can add Open Responses style WebSocket streaming to a Spring application with one dependency.

The gateway exposes a bidirectional WebSocket protocol (`response.create`, `response.cancel`, streaming deltas) and delegates model execution to Spring AI.

## Why This Exists

Open Responses promotes a shared interface for AI responses so clients can avoid provider-specific protocol handling and reduce coupling:

- https://www.openresponses.org/
- https://www.openresponses.org/specification
- https://www.openresponses.org/reference
- https://www.openresponses.org/compliance

This starter applies that idea to Spring applications that need:

- persistent WebSocket sessions for chat,
- low-latency token streaming,
- in-band cancellation,
- portable client protocol across OpenAI-compatible backends.

## Module Layout

- `openresponses-gateway-spring-boot`: core gateway implementation + auto-configuration.
- `openresponses-gateway-spring-boot-starter`: starter dependency for consumer apps.
- `openresponses-gateway-spring-boot-starter-test`: test support utilities — `OpenResponsesTestClient` for WebSocket integration tests and `OpenResponsesAssertions` for protocol event assertions.
- `openresponses-gateway-sample`: runnable sample application using the starter.

## Quick Start (Run the Sample)

1. Set environment variables:

```bash
OPENAI_API_KEY=...
OPENAI_BASE_URL=https://api.openai.com
OPENAI_DEFAULT_MODEL=gpt-4.1-mini
OPENRESPONSES_GATEWAY_ENABLED=true
OPENRESPONSES_GATEWAY_PATH=/ws/responses
OPENRESPONSES_GATEWAY_PROVIDER_TIMEOUT=90s
OPENRESPONSES_GATEWAY_MAX_HISTORY_ITEMS=100
OPENRESPONSES_GATEWAY_MAX_STORED_RESPONSES=100
```

2. Start the sample app:

```bash
mvn -pl openresponses-gateway-sample -am spring-boot:run
```

3. Connect a WebSocket client to:

- `ws://localhost:8080/ws/responses`

Sample config location:

- `openresponses-gateway-sample/src/main/resources/application.yml`

## Use in Your Own Spring Boot App

Add the starter dependency:

```xml
<dependency>
  <groupId>de.papenhagen</groupId>
  <artifactId>openresponses-gateway-spring-boot-starter</artifactId>
  <version>0.1.0-SNAPSHOT</version>
</dependency>
```

Auto-configuration entry:

- `de.papenhagen.openresponses.gateway.autoconfigure.OpenResponsesGatewayAutoConfiguration`

Registration file:

- `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

## Configuration Properties

Prefix: `openresponses.gateway`

| Property                | Default          | Description                                                           |
|-------------------------|-----------------|----------------------------------------------------------------------|
| `enabled`               | `true`          | Enable or disable the gateway.                                       |
| `path`                  | `/ws/responses` | WebSocket endpoint path.                                             |
| `default-model`         | `gpt-4.1-mini`  | Model used when client omits `model` in `response.create`.             |
| `max-history-items`     | `100`           | Maximum history entries retained per session. Minimum: `1`.           |
| `max-stored-responses`  | `100`           | Maximum past responses retained per session. Minimum: `1`.           |
| `provider-timeout`      | `90s`           | Timeout for provider streaming calls. Minimum: `1ms`.                 |
| `session-store`         | `memory`        | Session repository: `memory` (default) or `redis`.                  |
| `session-ttl`          | `30m`           | TTL for sessions in Redis. Only used when `session-store=redis`.     |

Provider credentials are configured via Spring AI OpenAI properties:

- `spring.ai.openai.api-key`
- `spring.ai.openai.base-url`

## WebSocket Protocol

### Client event: `response.create`

```json
{
  "type": "response.create",
  "payload": {
    "model": "gpt-4.1-mini",
    "input": ["Summarize this architecture in 3 bullets."]
  }
}
```

`input` supports:

- string
- array of strings
- array of objects with text extracted from:
  - `text`
  - `content` (string)
  - `content[].text`

`previous_response_id` is supported for multi-turn continuation — the previous response output is prepended to the prompt automatically.

### Client event: `response.cancel`

```json
{
  "type": "response.cancel",
  "payload": {
    "response_id": "resp_abc123"
  }
}
```

### Server events

- `response.created`
- `response.output_text.delta`
- `response.completed`
- `response.error`
- `response.cancelled`

Success terminal event:

```json
{
  "type": "response.completed",
  "payload": {
    "response_id": "resp_abc123",
    "status": "completed",
    "output_text": "Hello world"
  }
}
```

Failure terminal sequence:

1. `response.error`
2. `response.completed` with `status: "failed"`

## Error Handling

When a provider call fails, the gateway emits a `response.error` event before the terminal `response.completed`. The `code` field distinguishes failure types:

| Code                    | Message                                    | Retryable | Cause                                        |
|-------------------------|--------------------------------------------|-----------|----------------------------------------------|
| `provider_timeout`      | Request timed out                          | Yes       | Network or provider timeout.                 |
| `provider_unauthorized` | Authentication failed                      | No        | Invalid or missing API key.                  |
| `provider_forbidden`    | Access denied                              | No        | Token lacks required permissions.            |
| `provider_rate_limited` | Rate limit exceeded                        | Yes       | Too many requests.                           |
| `provider_bad_request`  | Invalid request                            | No        | Malformed request to provider.               |
| `provider_unavailable`  | Provider temporarily unavailable           | Yes       | Provider is down or unreachable.             |
| `circuit_breaker_open`  | Circuit breaker open, provider unavailable | Yes       | Too many recent failures; wait for recovery. |
| `provider_error`        | Provider error                             | Varies    | Unclassified provider failure.               |

The `retryable` field on the error payload indicates whether the client should retry.

Internal details (API keys, file paths, internal IPs) are never leaked in error messages.

## Resilience

Provider calls are wrapped with a Resilience4j circuit breaker. After a configurable failure threshold, the breaker opens and immediately rejects new requests with `circuit_breaker_open` rather than hammering a struggling provider.

Default behavior uses `CircuitBreakerRegistry.ofDefaults()`. Override the `CircuitBreakerRegistry` bean to configure failure thresholds, wait times, and sliding window parameters.

## Session Persistence

Sessions store conversation history, past responses, and ordering state. Two storage backends are available:

### In-memory (default)

Sessions live in a `ConcurrentHashMap` on the JVM heap. Suitable for single-instance deployments or local development. Sessions are lost on restart.

### Redis

Set `openresponses.gateway.session-store=redis` to persist sessions across restarts and across multiple gateway instances. Active in-flight responses are ephemeral — only completed responses survive serialization.

Redis connection is configured via Spring Data Redis properties:

```bash
spring.data.redis.host=localhost
spring.data.redis.port=6379
```

Start Redis locally via the provided compose file:

```bash
docker compose up -d redis
```

Session TTL is controlled via `openresponses.gateway.session-ttl` (default: `30m`). Idle sessions expire automatically. Each gateway request refreshes the TTL.

## Tool Support

Tools are registered via Spring AI's tool system. The default starter configuration provides an `EchoTools` demo bean. Override the `gatewayTools` bean to register real tool implementations:

```java
@Bean
@Override
public List<Object> gatewayTools() {
    return List.of(new MyTool(), new AnotherTool());
}
```

To disable tools entirely, override with an empty list:

```java
@Bean
public List<Object> gatewayTools() {
    return List.of();
}
```

## Test Utilities

The `openresponses-gateway-spring-boot-starter-test` module provides WebSocket test utilities.

### OpenResponsesTestClient

```java
OpenResponsesTestClient client = OpenResponsesTestClient.connect("ws://localhost:8080/ws/responses");
client.connect();
client.sendResponseCreate("gpt-4.1-mini", List.of("Hello"));
List<String> messages = client.getMessages();
client.disconnect();
```

### OpenResponsesAssertions

```java
OpenResponsesAssertions assertions = OpenResponsesAssertions.using(objectMapper);
assertions.assertFirst(messages, assertions.hasEventType("response.created"));
assertions.assertLast(messages, assertions.hasStatus("completed"));
List<String> deltas = assertions.filterByType(messages, "response.output_text.delta");
```

## CI / CD

CI runs on every push to `main` and on pull requests. Release deploys to Maven Central on version tags (`v*`).

### Secrets required for release

- `GPG_PRIVATE_KEY` — ASCII-armored GPG private key.
- `GPG_PASSPHRASE` — GPG key passphrase.
- `SONATYPE_OSSRH_USERNAME` — Sonatype Jira username.
- `SONATYPE_OSSRH_PASSWORD` — Sonatype Jira password.

## Automated Dependency Updates

Dependabot keeps dependencies current via weekly pull requests:

- **GitHub Actions**: `actions/checkout`, `actions/setup-java`, etc.
- **Maven**: grouped by Spring Boot, Spring AI, and Resilience4j.

Open pull request limits are capped at 10 per ecosystem.

## Build & Validate

```bash
mvn test                        # run unit tests
mvn checkstyle:check            # run style checks
```

Both are executed by the CI pipeline on every push and pull request.
