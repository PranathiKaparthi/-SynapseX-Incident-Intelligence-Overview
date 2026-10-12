# SynapseX — AI-Powered Real-Time Distributed Incident Intelligence Platform

[![Build & Test Status](https://img.shields.io/badge/tests-29%20passed%20%2F%200%20failed-success.svg)](#testing)
[![Java 21](https://img.shields.io/badge/Java-21%20LTS-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.4-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![React](https://img.shields.io/badge/React-18-blue.svg)](https://react.dev/)
[![Vite](https://img.shields.io/badge/Vite-5.4-purple.svg)](https://vitejs.dev/)
[![Docker](https://img.shields.io/badge/Docker%20Compose-Ready-blue.svg)](docker-compose.yml)

**SynapseX** monitors telemetry across distributed services, detects threshold & statistical anomalies, autonomously correlates cascading multi-service failures into unified incidents via topological graph traversal, opens real-time STOMP war rooms with monotonic sequence ordering, executes multi-agent AI root-cause investigations citing verifiable database telemetry records, queries historical post-mortems via pgvector semantic embeddings, and replays incidents deterministically in a scrubbable Time Machine with *"What Changed?"* baseline diffing.

---

## 🌟 Key Features

* **Autonomous Graph Correlation:** Evaluates service dependency graphs via undirected Breadth-First Search (BFS) distance scoring and time-clustering to merge cascading upstream/downstream failures (e.g. `postgres-db` $\to$ `payment-service` $\to$ `order-service`) into a single incident while keeping isolated outages separate.
* **Real-Time War Rooms:** STOMP over WebSocket (`/ws/synapsex`) with strictly monotonic sequence numbers (`#1`, `#2`, `#3`), client message deduplication, live presence tracking, and automatic gap recovery (`since_seq`). Includes RBAC write protection (viewers are restricted to read-only access).
* **Multi-Agent AI Root-Cause Investigation:** Parallel subagents orchestrated via Java 21 Virtual Threads:
  * *Metric Anomaly Agent* (sliding window threshold breaches & EWMA drift)
  * *Log Analyzer Agent* (connection pool exhaustion & exception stack traces)
  * *Topology Tracer Agent* (dependency reachability and bottleneck isolation)
  * *RAG Memory Agent* (historical post-mortem retrieval via dense vector embeddings)
  * *Strict Evidence Validation:* Every claim must cite a valid `eventId` verified against the database; uncited claims are automatically dropped.
  * *Demo Mode Transparency:* Visibly badged `"DEMO — not a real AI result"` when external LLMs are not configured.
* **Deterministic Time Machine Replay & "What Changed?":**
  * Scrubbable chronological stream sorted by `(timestamp ASC, sequenceId ASC)`.
  * Computes metric percentage deltas, new error patterns, and dependency health between the incident window and a 30-minute pre-incident baseline.
* **Deterministic Failure Simulator:** 8 realistic failure scenarios (PostgreSQL Connection Exhaustion, API Latency Spike, CPU Saturation, JVM Heap Exhaustion, Kafka Consumer Lag, Cascading Outage, Payment Service Termination, and Normal Traffic) powered by a seedable pseudo-random generator.
* **Multi-Tenant Workspace Isolation:** Every query, incident, telemetry point, war room message, and vector embedding is strictly scoped to `workspace_id`.
* **Zero-Dependency Local Mode:** Seamless local execution powered by an in-memory database with automatic startup seeding, plus complete production Docker Compose support.

---

## 🏗️ Architecture

```
                              SYNAPSEX ARCHITECTURE
                              
  [ Telemetry Clients / Simulator ] (8 Seedable Failure Scenarios)
                 │
                 ▼  HTTP POST /api/telemetry/events (Token / API-Key Auth, Bucket4j Rate Limiting)
     ┌────────────────────────┐
     │   Ingestion Service    │ ──► (Deduplication on workspace_id:event_id)
     └───────────┬────────────┘
                 │ Kafka Topic: synapsex.telemetry.raw
                 ▼
     ┌────────────────────────┐
     │ Telemetry Consumer     │ ──► PostgreSQL (telemetry_events) & Metrics Counter
     └───────────┬────────────┘
                 │ Kafka Topic: synapsex.telemetry.processed
                 ▼
     ┌────────────────────────┐
     │ Anomaly Detection      │ ──► Sliding Window Rules & EWMA Drift Engine
     └───────────┬────────────┘
                 │ Kafka Topic: synapsex.anomalies
                 ▼
     ┌────────────────────────┐
     │ Correlation Engine     │ ──► Graph BFS Topological Reachability + Time Clustering
     └───────────┬────────────┘
                 │ Kafka Topic: synapsex.incidents
                 ▼
     ┌────────────────────────┐
     │ Incident Service       │ ──► Lifecycle State Machine (DETECTED ─► CLOSED)
     └───────────┬────────────┘
                 │
      ┌──────────┴─────────────────────────┬────────────────────────┐
      ▼                                    ▼                        ▼
┌──────────────────┐             ┌──────────────────┐     ┌──────────────────┐
│  AI Orchestrator │             │  War Room Relay  │     │ Notification Svc │
│  (Virtual Threads│             │  (Monotonic Seq  │     │ (Alert Fatigue   │
│   Multi-Agent)   │             │   & Presence)    │     │  Deduplication)  │
└────────┬─────────┘             └────────┬─────────┘     └────────┬─────────┘
         │                                │                        │
         │  pgvector RAG                  │  STOMP / SockJS        │  WebSocket Fan-Out
         ▼                                ▼                        ▼
┌────────────────────────────────────────────────────────────────────────────┐
│                    React 18 + Vite + Tailwind Dashboard                    │
│  [Overview KPI] [Live Incidents] [War Room] [AI Report] [Time Machine Replay]
└────────────────────────────────────────────────────────────────────────────┘
```

---

## 🚀 Quick Start

### 1. Prerequisites
- **Java:** JDK 21 LTS
- **Maven:** Apache Maven 3.9+
- **Node.js:** Node.js v20+ and npm

### 2. Run Locally (Zero Setup Required)

**Terminal 1 — Backend (Spring Boot 3 on port 8080):**
```powershell
cd backend
mvn spring-boot:run
```
*Swagger UI available at: `http://localhost:8080/swagger-ui.html`*

**Terminal 2 — Frontend (React 18 + Vite on port 3000):**
```powershell
cd frontend
npm run dev
```

Open **`http://localhost:3000`** in your browser.

---

### 3. Run with Docker Compose (Full Stack)
```bash
docker compose up -d
```
Starts:
- Frontend UI (`:3000`)
- Backend API (`:8080`)
- PostgreSQL 16 with pgvector (`:5432`)
- Redis 7 (`:6379`)
- Kafka KRaft (`:9092`)
- Prometheus (`:9090`)
- Grafana (`:3001`)

---

## 🔑 Seed User Accounts

SynapseX comes with pre-seeded users and 1-click login presets:

| Role | Email | Password | Permissions |
| :--- | :--- | :--- | :--- |
| **ENGINEER** | `alice@acme.com` | `Password123!` | Full triage, war room chat, AI investigation, RAG indexing |
| **ADMIN** | `admin@acme.com` | `Password123!` | Workspace settings, API keys, service dependency config |
| **INCIDENT_MANAGER** | `bob@acme.com` | `Password123!` | Severity escalation, incident closure, post-mortem sign-off |
| **VIEWER** | *(Assignable)* | `Password123!` | Read-only access to dashboard and war rooms (message posting blocked) |

---

## 🧪 Testing & Verification

### Backend Automated Test Suite (100% Pass Rate):
```bash
cd backend
mvn test
```
* **29 Tests Run, 0 Failures:**
  * `AuthServiceTest` (5/5) — JWT, refresh token rotation, reuse detection, workspace isolation.
  * `IngestionServiceTest` (5/5) — API key auth, batch HTTP 207, rate limiting, consumer idempotency.
  * `CorrelationAndLifecycleTest` (2/2) — Topological BFS graph correlation and lifecycle transitions.
  * `WarRoomServiceTest` (5/5) — Monotonic sequence numbers, gap recovery, presence tracking, VIEWER role write blocking.
  * `AiInvestigationTest` (4/4) — Injection defense, unverified citation dropping, fallback logic, DemoProvider transparency.
  * `RagServiceTest` (3/3) — Dense embeddings, cosine similarity ranking, multi-tenant isolation.
  * `TimeMachineServiceTest` (2/2) — Deterministic byte-identical replay stream, "What Changed?" baseline diff.
  * `NotificationServiceTest` (3/3) — WebSocket broadcast, alert fatigue deduplication.

### Frontend Test Suite:
```bash
cd frontend
npm test
```
* 3/3 component and routing tests passed with Vitest and React Testing Library.

---

## 📁 Repository Structure

```
├── backend/                  # Spring Boot 3 modular monolith (Java 21)
│   ├── src/main/java/com/synapsex/
│   │   ├── auth/             # JWT, refresh rotation, RBAC, workspaces
│   │   ├── ingestion/        # Rate limiting, batch processing, idempotency
│   │   ├── processing/       # Detection engine, anomaly rules, sliding windows
│   │   ├── incident/         # Topological BFS correlation engine, lifecycle
│   │   ├── collaboration/    # STOMP war rooms, sequence numbering, presence
│   │   ├── ai/               # Multi-agent orchestrator, prompt sanitizer, validator
│   │   ├── knowledge/        # RAG memory, 384-dim dense vectors, cosine search
│   │   ├── replay/           # Time Machine replay stream, What Changed diff
│   │   ├── notification/     # In-app notification center, alert fatigue dedupe
│   │   └── simulation/       # 8 seedable failure generators
│   └── src/main/resources/
│       ├── application.yml   # Production configuration
│       ├── application-dev.yml # In-memory zero-dependency local configuration
│       └── db/migration/     # Flyway PostgreSQL migrations (V1 schema, V2 seed)
├── frontend/                 # React 18 + Vite + TypeScript + Tailwind CSS
│   ├── src/
│   │   ├── components/       # Layout, WarRoom, AiInvestigationView, TimeMachine
│   │   ├── pages/            # Dashboard, Incidents, Details, Simulations, Knowledge
│   │   ├── store/            # Zustand stores (useAuthStore, useSocketStore)
│   │   └── api/              # ApiClient with automatic token refresh
├── infrastructure/           # Prometheus and Grafana provisioning & dashboards
├── docs/                     # Specifications, ADRs (001-009), verification guides
└── docker-compose.yml        # Full containerized multi-container deployment
```

---

## 📄 License

MIT License &copy; 2026 SynapseX Engineering Team.
