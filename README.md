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
- `openresponses-gateway-spring-boot-starter-test`: starter test support module scaffold.
- `openresponses-gateway-sample`: runnable sample application using the starter.

## Quick Start (Run the Sample)

1. Set environment variables:

```bash
OPENAI_API_KEY=...
OPENAI_BASE_URL=https://api.openai.com
OPENAI_DEFAULT_MODEL=gpt-4.1-mini
OPENRESPONSES_GATEWAY_ENABLED=true
OPENRESPONSES_GATEWAY_PATH=/ws/responses
OPENRESPONSES_PROVIDER_TIMEOUT=90s
APP_MAX_HISTORY_ITEMS=100
APP_MAX_STORED_RESPONSES=100
```

2. Start Redis for local dev/test:

```bash
docker compose up -d redis
```

3. Run the sample app:

```bash
mvn -pl openresponses-gateway-sample -am spring-boot:run
```

4. Connect WebSocket client to:

- `ws://localhost:8080/ws/responses`

Sample config location:

- `openresponses-gateway-sample/src/main/resources/application.yml`

## Redis (Dev/Test)

For local development and integration testing, a Redis service is available via:

- `docker-compose.yml`
- service: `redis`
- port: `6379`

Common commands:

```bash
docker compose up -d redis
docker compose ps
docker compose logs -f redis
docker compose down
```

Note: the current gateway implementation keeps session state in-memory. Redis is provided here as local infrastructure for dev/test workflows and upcoming persistence integration.

## Use in Your Own Spring Boot App

If this project is installed/deployed to your Maven repository, add:

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

- `openresponses.gateway.enabled` (default: `true`)
- `openresponses.gateway.path` (default: `/ws/responses`)
- `openresponses.gateway.default-model` (default: `gpt-4.1-mini`)
- `openresponses.gateway.max-history-items` (default: `100`)
- `openresponses.gateway.max-stored-responses` (default: `100`)
- `openresponses.gateway.provider-timeout` (default: `90s`)

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
- array objects with text extracted from:
  - `text`
  - `content` (string)
  - `content[].text`

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

## Validation

Commands executed and passing on current structure:

```bash
mvn test -q
```

This validates:

- multi-module Maven wiring,
- starter/core compilation,
- existing gateway behavior tests in `openresponses-gateway-spring-boot`,
- protocol lifecycle, error handling, and provider adapter behavior.

## Notes

- This is a focused gateway/starter, not a complete implementation of all Open Responses specification features.
- The `openresponses-gateway-spring-boot-starter-test` module is currently a scaffold and can be expanded with reusable client/assertion utilities.
