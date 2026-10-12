# Architecture Decisions (ADR Log)

## ADR-001: Backend Architecture as Modular Monolith
- **Status:** Accepted
- **Context:** SynapseX coordinates complex workflows (telemetry ingestion, anomaly detection, graph correlation, AI agent investigation, STOMP war rooms, time-machine replay). Distributing into 8 microservices immediately introduces network hop overhead, distributed transaction failures, complex local development, and distributed tracing complexity before domain boundaries stabilize.
- **Decision:** Build SynapseX as a strictly modular Spring Boot 3.3 monolith in Java 21 with decoupled module boundaries:
  - `auth`
  - `workspace`
  - `ingestion`
  - `processing` (detection & correlation)
  - `incident`
  - `collaboration` (war rooms)
  - `ai` (orchestration, agents, provider abstraction)
  - `knowledge` (RAG, pgvector)
  - `notification`
  - `simulation`
  - `replay`
  - `common`
  Modules communicate via application service interfaces and internal event buses / Kafka topics, never by direct cross-boundary entity repository writes.
- **Extraction Candidates:** When scaling to high sustained volume, `ingestion` and `simulation` are the first candidates to split into independent microservices, as they are stateless high-throughput data pipes.

## ADR-002: In-Process API Gateway Filters
- **Status:** Accepted
- **Context:** Need gateway responsibilities including JWT validation, workspace tenant resolution, per-client token bucket rate limiting, request size boundaries, CORS handling, and request-scoped MDC correlation IDs (`traceId`).
- **Decision:** Implement these as an ordered chain of Spring Security and Servlet Filters (`OncePerRequestFilter`) rather than an external gateway container. This eliminates extraneous network latency and simplifies single-node deployment while providing uniform enforcement.

## ADR-003: Messaging & Ordering Guarantees
- **Status:** Accepted
- **Context:** Distributed telemetry requires high-throughput processing. Global ordering across all services is physically infeasible and unnecessary.
- **Decision:** Kafka partition keys are strictly formatted as `${workspaceId}:${serviceName}`. This guarantees strict per-service temporal order while enabling horizontal parallel processing across services. Consumers use manual acknowledgment after successful DB write / anomaly evaluation, paired with an idempotent processed-event ledger `(workspace_id, event_id)` for at-least-once delivery semantics. Retry topics with exponential backoff (`.retry`) and dead-letter queues (`.dlt`) handle poison pills.

## ADR-004: Dual-Write Mitigation via Outbox Pattern
- **Status:** Accepted
- **Context:** State changes in the incident engine must synchronize with Kafka topics (`synapsex.incidents`, `synapsex.ai.tasks`, `synapsex.notifications`) without 2-phase commit (2PC).
- **Decision:** Implement the Transactional Outbox pattern. Incident mutations persist the domain entity and an `outbox` record in the same atomic PostgreSQL transaction. An asynchronous scheduled relay process publishes outbox events to Kafka and flags them as processed.

## ADR-005: AI Provider Abstraction & Safe Demo Fallback
- **Status:** Accepted
- **Context:** System requires flexible AI integration with OpenAI, Anthropic, or Ollama, but must run deterministically and immediately out of the box without requiring external paid API keys or failing in offline environments.
- **Decision:** Implement an `LlmProvider` and `EmbeddingProvider` interface abstraction. If no API key is provided, the platform transparently falls back to `DemoProvider`, which executes deterministic heuristic investigations against the actual stored telemetry and dependencies, explicitly tagging all outputs with `mode: "DEMO"`.

## ADR-006: RAG Incident Memory Storage with pgvector
- **Status:** Accepted
- **Context:** Similar incident retrieval requires dense vector search alongside strict relational tenancy filtering.
- **Decision:** Use PostgreSQL with the `pgvector` extension (`vector(384)` or `vector(1536)`). Use HNSW index for high-recall cosine distance queries. The SQL query explicitly enforces `WHERE workspace_id = :workspaceId AND 1 - (embedding <=> :query) >= :cutoff` directly at the index level to prevent tenant data leakage.

## ADR-007: War Room Real-Time Messaging & Gap Recovery
- **Status:** Accepted
- **Context:** War rooms require low-latency collaborative chat, presence, and guaranteed message continuity through client network disconnections.
- **Decision:** WebSocket with STOMP protocol. PostgreSQL acts as the single source of durable truth for all messages with strictly monotonic sequence numbers (`seq`) per room. Redis is used solely for transient presence (heartbeat key TTLs) and ephemeral fan-out. On reconnect, clients request `GET /api/incidents/{id}/messages?since_seq={lastSeenSeq}` to backfill missed messages.

## ADR-008: Deterministic Incident Time Machine
- **Status:** Accepted
- **Context:** Post-mortem analysis requires deterministic replay of timeline events, metric curves, and AI hypothesis evolutions.
- **Decision:** Replay events are materialized exclusively from historical append-only database tables (`telemetry_events`, `anomalies`, `incident_events`, `messages`, `ai_investigations`), stably ordered by `(event_time ASC, sequence_id ASC)`. Replay produces bit-identical event streams for identical time windows.

## ADR-009: Pinned Stable Dependency Versions
- **Status:** Accepted
- **Java:** OpenJDK 21 LTS (`21.0.12.1`)
- **Maven:** 3.9.9
- **Spring Boot:** 3.3.4
- **Spring Kafka:** managed by Spring Boot 3.3.4
- **Spring Security & Data JPA:** managed by Spring Boot 3.3.4
- **JJWT:** `0.12.6` (`io.jsonwebtoken:jjwt-api`, `jjwt-impl`, `jjwt-jackson`)
- **PostgreSQL JDBC:** `42.7.4`
- **pgvector-java:** `0.1.6` (`com.pgvector:pgvector`)
- **Flyway:** `10.15.0` (`flyway-core`, `flyway-database-postgresql`)
- **Bucket4j:** `8.10.1` (`com.bucket4j:bucket4j-core`)
- **Springdoc OpenAPI:** `2.6.0` (`springdoc-openapi-starter-webmvc-ui`)
- **Resilience4j:** `2.2.0` (`resilience4j-spring-boot3`)
- **Frontend Stack:** React 18 / Vite 5, TypeScript 5.5, Tailwind CSS 3.4, React Router 6.26, TanStack Query 5.56, Zustand 4.5, Recharts 2.12, `@stomp/stompjs` 7.0.0, Lucide React 0.441.

