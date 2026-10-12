# SynapseX Distributed Systems Specification

## 1. Ordering Guarantees
- **Global Ordering Impossibility**: In a multi-tenant platform ingesting millions of telemetry metrics across hundreds of distributed microservices, achieving a globally synchronized total ordering across all services introduces severe lock contention and clock synchronization bottlenecks.
- **Service-Level Total Ordering**: Telemetry partition keys in Kafka are structured as `${workspaceId}:${serviceName}`. All events originating from the same service in the same workspace are guaranteed to be delivered to the exact same Kafka partition in strict FIFO order according to Kafka's partition offset semantics.
- **Cross-Service Event Windows**: For correlation and sliding window anomaly detection across services, SynapseX relies on `event_time` (stored in UTC ISO-8601) with a configurable late-arrival tolerance window (default: 60 seconds). Out-of-order events arriving within the window are merged into the time-series window.

## 2. Delivery Semantics & Idempotency
- **At-Least-Once Delivery**: Producers publish to Kafka with `acks=all`. Consumers process messages and commit offsets manually after transaction commit.
- **Idempotent Consumers**: Every telemetry event has a globally unique `eventId` provided by the client (UUIDv4). The persistence layer checks an append-only `processed_events` table with unique constraint `(workspace_id, event_id)`. Re-delivered duplicates are acknowledged and silently ignored without corrupting sliding-window aggregates or duplicating incidents.
- **Outbox Pattern for Dual-Writes**: When an incident state change occurs, the incident record update and an `outbox` event record are committed in the same database transaction. A background relay thread polls the outbox and publishes events to `synapsex.incidents`, ensuring eventual consistency between the database and Kafka.

## 3. Backpressure & Flow Control
- **Bounded Ingestion Queues**: The HTTP ingestion gateway enforces token-bucket rate limiting per API key. When downstream Kafka brokers experience pressure or saturation, the gateway applies backpressure by rejecting excess requests with HTTP 429 (`Too Many Requests`) and a `Retry-After` header.
- **Consumer Throttling**: Kafka consumers configure `max.poll.records=500` and utilize Spring Kafka's `pause()` and `resume()` lifecycle hooks when downstream PostgreSQL connection pool saturation exceeds 90%.

## 4. Failure Scenarios, Retries & Dead Letter Topics
- **Transient Failures**: Database connection timeouts or deadlock exceptions trigger automatic consumer retry with exponential backoff (e.g. initial 1s, multiplier 2.0, max 3 attempts) published to `synapsex.telemetry.raw.retry`.
- **Poison Pills**: Malformed event payloads or corrupt records that fail deserialization/validation are routed directly to `synapsex.telemetry.raw.dlt` (Dead Letter Topic) without halting the consumer partition. DLT events are indexed for inspection.
- **Graceful Shutdown**: On `SIGTERM`, Spring Boot initiates a graceful drain of active in-flight Kafka batches and WebSocket sessions up to a 30-second shutdown deadline.
