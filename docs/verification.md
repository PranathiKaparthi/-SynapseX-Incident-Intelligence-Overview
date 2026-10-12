# SynapseX — Verification & Run Guide

This document describes how to build, test, run, and verify **SynapseX — AI-Powered Real-Time Distributed Incident Intelligence Platform**.

---

## 1. Prerequisites

- **Java:** JDK 21 LTS (`java -version`)
- **Maven:** Apache Maven 3.9+ (`mvn -v`)
- **Node.js:** Node.js v20+ (`node -v`) and npm (`npm -v`)
- **Docker & Docker Compose:** Optional for local development; required for complete containerized production deployment.

---

## 2. Running Automated Tests

### 2.1 Backend Tests (JUnit 5 + Spring Boot)
The backend test suite verifies all layers: authentication with refresh token rotation and reuse detection, rate limiting, consumer idempotency, topological BFS incident correlation, war rooms with sequence ordering and presence, prompt injection defense, validated AI citations, pgvector RAG memory, notification deduplication, and deterministic Time Machine replay.

```bash
cd backend
mvn test
```
*Expected Result:* 29 tests run, 0 failures, 0 errors.

### 2.2 Frontend Tests (Vitest + React Testing Library)
The frontend test suite verifies application routing, unauthenticated screen guards, login presets, and KPI metrics rendering.

```bash
cd frontend
npm test
```
*Expected Result:* 3 tests run across 2 test files, 0 failures.

### 2.3 Frontend Production Build
```bash
cd frontend
npm run build
```
*Expected Result:* TypeScript validation passes and Vite outputs minified assets to `frontend/dist/`.

---

## 3. Running SynapseX Locally

### Option A: Complete Docker Compose Stack
From the project root:
```bash
docker compose up -d
```
This launches:
- `synapsex-postgres`: PostgreSQL 16 with `pgvector` extension (port 5432)
- `synapsex-redis`: Redis 7 (port 6379)
- `synapsex-kafka`: Apache Kafka 3.7 KRaft mode (port 9092)
- `synapsex-backend`: Spring Boot 3 application (port 8080)
- `synapsex-frontend`: React production UI served via Nginx (port 3000)
- `synapsex-prometheus`: Metrics scraper (port 9090)
- `synapsex-grafana`: Pre-provisioned dashboards (port 3001)

### Option B: Local Development Mode (Backend + Frontend)
1. **Start Backend:**
   ```bash
   cd backend
   mvn spring-boot:run
   ```
   The backend starts at `http://localhost:8080`. Flyway automatically applies migrations `V1__initial_schema.sql` and `V2__seed_data.sql`.
   Swagger OpenAPI documentation is available at `http://localhost:8080/swagger-ui.html`.

2. **Start Frontend:**
   ```bash
   cd frontend
   npm run dev
   ```
   The Vite dev server starts at `http://localhost:3000`.

---

## 4. Default Seed Credentials

SynapseX is seeded with an initial workspace (`Acme Corp Production`) and three users with distinct roles:

| Role | Email | Password | Permissions |
| :--- | :--- | :--- | :--- |
| **ENGINEER** | `alice@acme.com` | `Password123!` | Full incident triage, war room chat, AI investigation, RAG indexing |
| **ADMIN** | `admin@acme.com` | `Password123!` | Workspace management, API key generation, team configuration |
| **INCIDENT_MANAGER** | `bob@acme.com` | `Password123!` | Severity escalation, incident closure, post-mortem sign-off |
| **VIEWER** | *(Assignable)* | `Password123!` | Read-only access to dashboard and war rooms (message posting blocked) |

*Tip: The login page includes one-click preset buttons for instant authentication.*

---

## 5. End-to-End Incident Verification Walkthrough

1. **Sign in to SynapseX:**
   Open `http://localhost:3000` and click "Engineer" to authenticate as Alice.

2. **Launch a Simulation:**
   - Navigate to the **Simulations** tab (`/simulations`).
   - Select **PostgreSQL Connection Exhaustion** (or API Latency Increase).
   - Click **Start Generator**.
   - The simulator emits events tagged `simulated=true` with seed `42`.

3. **Observe Autonomous Correlation on the Dashboard:**
   - Navigate to the **Dashboard** (`/`).
   - Notice the active incident KPI count increase and the cascading services (`postgres-db`, `payment-service`, `order-service`) flagged.
   - The topological correlation engine correlates upstream/downstream anomalies into a single consolidated incident.

4. **Enter the Incident War Room:**
   - Click **Enter War Room** for the active incident.
   - Post an investigation note in the real-time chat.
   - Notice the monotonic sequence numbers (`#1`, `#2`, `#3`) and live presence indicator.

5. **Run Multi-Agent AI Root-Cause Investigation:**
   - Click the **AI Root-Cause Investigation** tab.
   - Click **Execute AI Investigation**.
   - Subagents execute in parallel (Metric Anomaly, Log Analyzer, Topology Tracer, RAG Memory).
   - Review the verified hypotheses, confidence rating, and clickable telemetry evidence citations (`eventId`).
   - Notice the clear `"DEMO — not a real AI result"` badge ensuring total transparency.

6. **Deterministic Time Machine Replay & "What Changed?":**
   - Click the **Time Machine Replay** tab.
   - Click **Play Replay** or scrub through the timeline.
   - Inspect the **"What Changed?"** diff showing metric percentage deltas against the 30-minute pre-incident baseline.

7. **Query Incident Memory (RAG):**
   - Navigate to **Knowledge Base** (`/knowledge`).
   - Search for `HikariCP connection pool timeout`.
   - Inspect cosine similarity matches scored against pgvector dense embeddings.
