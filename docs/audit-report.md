# SynapseX — Architectural Audit & Baseline Verification Report

**Date:** 2026-10-13  
**Branch:** `optimization-and-benchmarking`  
**Platform Version:** 1.0.0-SNAPSHOT  
**Engineers:** Principal Systems Architect & Performance Lead  

---

## 1. Executive Summary

SynapseX is an AI-powered, real-time distributed incident intelligence platform built to monitor telemetry, detect operational anomalies, correlate cascading service failures into actionable incidents, coordinate mitigation via live war rooms, and automate root-cause investigation using multi-agent AI and RAG memory.

This report establishes the baseline architectural state, verifies existing test coverage, diagnoses structural performance bottlenecks, and outlines the engineering roadmap to achieve:
1. $\ge 1,000$ successfully processed telemetry events per second sustained.
2. $\text{P95}$ event-ingestion latency $< 1.0$ second under load.
3. $\ge 30\%$ reduction in mean time to root cause (MTTRC) with validated diagnostic accuracy.
4. $99.9\%$ operational availability with automated failure recovery.

---

## 2. Technology Stack & Runtime Topology

### 2.1 Backend Architecture
- **Language / Runtime:** Java 21 LTS (OpenJDK Hotspot 21.0.12)
- **Framework:** Spring Boot 3.3.4 (Modular Monolith)
- **Persistence:** Spring Data JPA / Hibernate 6, PostgreSQL 16 with `pgvector` (production profile), H2 in-memory with PostgreSQL compatibility mode (dev/test profile)
- **Messaging:** Apache Kafka KRaft 3.7+ (`spring-kafka`), dual-topic pipeline (`telemetry-raw` $\rightarrow$ `telemetry-processed`, DLT: `telemetry-raw.dlt`)
- **Caching & Rate Limiting:** Redis 7 (cluster-ready), Bucket4j 8.10.1 in-memory token bucket
- **Resilience:** Resilience4j 2.2.0 (Circuit Breakers, Retry policies)
- **Security:** Spring Security 6, JJWT 0.12.6 (HMAC-SHA256, access + refresh token rotation, salted SHA-256 API key hashing)
- **Observability:** Micrometer Actuator, Prometheus Registry (`/actuator/prometheus`), custom counters/timers (`synapsex_telemetry_ingested_total`, `synapsex_anomalies_detected_total`)
- **Concurrent Concurrency Model:** Java 21 Virtual Threads (`Executors.newVirtualThreadPerTaskExecutor()`) for I/O-bound subagent orchestration

### 2.2 Frontend Architecture
- **Framework:** React 18 with TypeScript 5.5
- **Bundler:** Vite 5.4
- **Styling:** Tailwind CSS 3.4
- **Real-Time Streaming:** SockJS-client + StompJS over WebSocket (`/ws-warroom`), with automatic exponential backoff reconnection
- **State & Routing:** Context API + React Router 6

---

## 3. End-to-End Event Lifecycle & Data Flow

```
[Telemetry Producers / Agents]
            │ HTTP POST (/api/telemetry/events or /batch)
            ▼
┌───────────────────────────────────────────────┐
│ Ingestion Layer                               │
│  - API Key Auth (SHA-256 hash lookup)         │
│  - Rate Limiter (Bucket4j: 1000 rps, 2000 bst)│
│  - Schema Validation (TelemetryEnvelopeDto)   │
└───────────────────────┬───────────────────────┘
                        │ Kafka Producer
                        ▼
           [Kafka: telemetry-raw Topic]
                        │
                        ▼
┌───────────────────────────────────────────────┐
│ Processing & Anomaly Detection                │
│  - Idempotency Filter (ProcessedEvent check)  │
│  - Batch / Single Persistence (TelemetryEvent)│
│  - DetectionEngine (Sliding Window, EWMA)     │
└───────────────────────┬───────────────────────┘
                        │ Kafka: telemetry-processed
                        ▼
┌───────────────────────────────────────────────┐
│ Correlation & Incident Lifecycle              │
│  - Temporal Windowing (15-min proximity)      │
│  - Topological Distance Scoring (BFS graph)   │
│  - Auto-escalate or Open Incident             │
└───────────────────────┬───────────────────────┘
                        │ Trigger
                        ▼
┌───────────────────────────────────────────────┐
│ Multi-Agent AI & War Room Collaboration       │
│  - 4 Virtual Thread Agents (Telemetry, Logs,  │
│    Topology, RAG vector cosine similarity)    │
│  - Prompt Sanitizer & Evidence Validator      │
│  - Live WebSocket Broadcast to War Room       │
└───────────────────────────────────────────────┘
```

---

## 4. Baseline Verification Results

### 4.1 Backend Test Suite (`mvn clean test`)
| Test Suite Class | Tests Run | Failures | Errors | Execution Duration | Status |
| :--- | :---: | :---: | :---: | :---: | :---: |
| `AiInvestigationTest` | 4 | 0 | 0 | 4.81s | **PASSED** |
| `AuthServiceTest` | 4 | 0 | 0 | 1.95s | **PASSED** |
| `WarRoomServiceTest` | 4 | 0 | 0 | 1.87s | **PASSED** |
| `CorrelationAndLifecycleTest` | 4 | 0 | 0 | 2.15s | **PASSED** |
| `IngestionServiceTest` | 4 | 0 | 0 | 2.40s | **PASSED** |
| `RagServiceTest` | 3 | 0 | 0 | 1.80s | **PASSED** |
| `NotificationServiceTest` | 3 | 0 | 0 | 1.92s | **PASSED** |
| `TimeMachineServiceTest` | 3 | 0 | 0 | 2.10s | **PASSED** |
| **Total Backend Baseline** | **29** | **0** | **0** | **21.0s** | **100% PASS** |

### 4.2 Frontend Test Suite (`npm test` & `npm run build`)
| Test Suite | Tests Run | Pass | Fail | Duration | Status |
| :--- | :---: | :---: | :---: | :---: | :---: |
| `App.test.tsx` (Routing & Nav) | 1 | 1 | 0 | 0.82s | **PASSED** |
| `AuthContext.test.tsx` (Auth flow) | 1 | 1 | 0 | 0.35s | **PASSED** |
| `Dashboard.test.tsx` (Telemetry stats) | 1 | 1 | 0 | 0.28s | **PASSED** |
| **Vite Production Build** | — | — | — | 11.2s | **BUILT (0 errors)** |

---

## 5. Architectural Bottleneck Analysis & Optimization Strategy

| Subsystem | Identified Bottleneck | Optimization Strategy | Target Metric |
| :--- | :--- | :--- | :--- |
| **Ingestion Layer** | Synchronous iteration in `ingestBatch()` invoking individual rate limit checks and individual Kafka sends sequentially. | Batch token consumption, asynchronous concurrent Kafka publishing with `CompletableFuture.allOf()`, and JDBC batching. | $\ge 1,000$ events/s, P95 $< 1.0$s |
| **Processing Consumer** | Row-by-row JPA persistence (`save()`) per Kafka record causes high DB round-trip overhead under high ingress. | Micro-batching consumer with `saveAll()` / JDBC batch statements and tuned HikariCP connection pool. | Sustained 1,000 evt/s with zero queue pileup |
| **Correlation Engine** | Repeated database querying of service dependency graphs during each anomaly evaluation. | Cached in-memory adjacency list with sliding time-window invalidation. | Correlation time $< 5$ms per anomaly |
| **AI Investigation** | Sequential heuristic diagnosis baseline takes $\sim 450$–$800$ms on multi-symptom clusters; RAG embeddings deserialized repeatedly. | Parallel Virtual Thread execution across agents with cached normalized embeddings. | $\ge 30\%$ speedup vs baseline, $> 90\%$ accuracy |
| **Reliability Layer** | Lack of explicit availability percentage tracking and MTTR/MTBF metrics in the management actuator. | Dynamic Availability Monitor tracking uptime, successful transaction ratio, and failure recovery state. | Measured 99.9% design availability |

---

## 6. Next Actions

1. Author and run the **Reproducible Load Testing Harness** to measure baseline throughput and latency distribution (P50, P90, P95, P99).
2. Implement **High-Throughput Ingestion Optimizations** and verify against target thresholds.
3. Implement the **Incident Replay Evaluation Framework** with $\ge 20$ labeled incidents to benchmark MTTRC speedup and accuracy.
4. Author the **Reliability & Availability Monitoring Framework**.
5. Document all findings and verifiable metrics in `docs/performance-report.md` and `docs/resume-metrics.md`.
