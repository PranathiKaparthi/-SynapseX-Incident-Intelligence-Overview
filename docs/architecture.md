# SynapseX Architecture Specification

## 1. System Overview
SynapseX is an AI-powered real-time distributed incident intelligence platform. It ingests high-volume telemetry from distributed microservices, executes stream-based rule and statistical anomaly detection, correlates anomalies into unified incidents via topological dependency graphs and time-window clustering, orchestrates multi-agent AI root cause investigation, powers collaborative war rooms over WebSockets, and provides deterministic Time Machine replay with "What Changed?" analysis.

```mermaid
flowchart TD
    subgraph Ingestion Tier
        A[Microservices / Telemetry Agents] -->|POST /api/telemetry/events| B[Ingestion Gateway Filter]
        B -->|Auth & Rate Limit| C[Ingestion Controller]
        C -->|Publish Partition Key: workspace:service| D[(Kafka: synapsex.telemetry.raw)]
    end

    subgraph Processing Tier
        D --> E[Processing Consumer Engine]
        E -->|Deduplicate & Normalize| F[(PostgreSQL: telemetry_events)]
        E -->|Publish| G[(Kafka: synapsex.telemetry.processed)]
        G --> H[Detection Engine: Sliding Windows & Rules]
        H -->|Anomaly Detected| I[(Kafka: synapsex.anomalies)]
    end

    subgraph Incident & Intelligence Tier
        I --> J[Correlation Engine: Topology & Window Graph]
        J -->|Correlate / Escalate| K[Incident Service]
        K -->|Outbox Pattern| L[(Kafka: synapsex.incidents)]
        L --> M[AI Investigation Orchestrator]
        L --> N[Notification Engine]
        L --> O[WebSocket STOMP Relay]
    end

    subgraph Multi-Agent AI Tier
        M -->|Parallel Dispatch| P1[Telemetry Agent]
        M -->|Parallel Dispatch| P2[Log Agent]
        M -->|Parallel Dispatch| P3[Dependency Agent]
        M -->|Parallel Dispatch| P4[RAG Historical Agent]
        P1 & P2 & P3 & P4 --> Q[Review Agent]
        Q -->|Structured Report Schema| R[(PostgreSQL: ai_investigations)]
    end

    subgraph Client & Collaboration Tier
        O --> S[React Dashboard & Incident War Rooms]
        N --> S
        S -->|Deterministic Replay Stream| T[Time Machine Engine]
    end
```

## 2. Component Boundaries & Responsibilities

### 2.1 Backend Modules (`com.synapsex`)
- **`auth`**: User registration, login, JWT issuance, refresh token rotation with reuse detection, BCrypt hashing, and Spring Security configuration.
- **`workspace`**: Multi-tenant workspace entities, memberships, role assignments (`ADMIN`, `ENGINEER`, `INCIDENT_MANAGER`, `VIEWER`), and tenancy context resolvers.
- **`ingestion`**: Secure high-throughput REST ingestion endpoints (`/events`, `/batch`), API key authorization, schema validation, rate-limiting filter, deduplication ledger, and Kafka raw producer.
- **`processing`**: Kafka consumer group for telemetry normalization, persistence, sliding-window aggregator, and anomaly detection engine (rule threshold + recovery detection).
- **`incident`**: Correlation engine analyzing service dependency graphs and temporal proximity, SEV1-SEV4 calculation, incident state machine (`DETECTED` -> `INVESTIGATING` -> `MITIGATING` -> `RESOLVED` -> `CLOSED`), timeline events, optimistic locking (`@Version`).
- **`collaboration`**: Real-time STOMP WebSocket endpoints (`/topic/incidents/{id}/war-room`), message persistence, monotonic room sequence numbers, read receipts, Redis presence heartbeats.
- **`ai`**: Provider abstraction (`LlmProvider`, `EmbeddingProvider`), `DemoProvider` heuristic engine, parallel multi-agent runner (virtual threads), strict JSON schema validator, prompt injection sanitizer.
- **`knowledge`**: Incident resolution embedding generator, `pgvector` HNSW store, multi-tenant scoped semantic search (`/api/knowledge/search`).
- **`replay`**: Deterministic event-stream builder for Time Machine, "What Changed?" baseline-vs-incident delta comparator.
- **`notification`**: In-app notifications, user preference matrix, WebSocket broadcast.
- **`simulation`**: Seedable failure scenario generator (8 canonical scenarios: CPU spike, memory leak, DB connection pool starvation, latency surge, Kafka lag, payment failure, cascade failure, network partition).
- **`common`**: RFC 7807 `ProblemDetail` handlers, audit logger, MDC filter (`traceId`, `workspaceId`).

## 3. High-Throughput Ingestion & Kafka Partitioning Strategy
- **Partition Key**: Format `${workspaceId}:${serviceName}` ensures per-service ordered arrival while scaling partitions across cluster nodes.
- **Delivery Guarantee**: At-least-once delivery with deduplication via `(workspace_id, event_id)` uniqueness.
- **Failure Isolation**: Retry topics with exponential backoff (`synapsex.telemetry.raw.retry`) and Dead Letter Topic (`synapsex.telemetry.raw.dlt`).
