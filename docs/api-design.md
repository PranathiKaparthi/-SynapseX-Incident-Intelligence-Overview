# SynapseX API Design & Contracts

## 1. Authentication Endpoints
- `POST /api/auth/register`
  - Body: `{ email, password, fullName, workspaceName }`
  - Response 201: `{ user: { id, email, fullName, role }, workspace: { id, name, slug }, accessToken, refreshToken }`
- `POST /api/auth/login`
  - Body: `{ email, password }`
  - Response 200: `{ user, workspace, accessToken, refreshToken }`
- `POST /api/auth/refresh`
  - Body: `{ refreshToken }`
  - Response 200: `{ accessToken, refreshToken }` (Rotated refresh token)
- `POST /api/auth/logout`
  - Response 204
- `GET /api/auth/me`
  - Response 200: Current user profile, memberships, and active workspace

## 2. Ingestion Endpoints
- `POST /api/telemetry/events`
  - Headers: `X-API-Key: syn_live_...`
  - Body: Canonical Telemetry Envelope
  - Response 202: `{ eventId: "...", status: "ACCEPTED", ingestedTime: "2026-10-12T10:30:00.123Z" }`
- `POST /api/telemetry/batch`
  - Headers: `X-API-Key: syn_live_...`
  - Body: Array of up to 500 events
  - Response 207 Multi-Status:
    ```json
    {
      "total": 50,
      "accepted": 49,
      "rejected": 1,
      "results": [
        { "eventId": "evt-001", "status": "ACCEPTED" },
        { "eventId": "evt-002", "status": "REJECTED", "reason": "SCHEMA_VALIDATION_FAILED" }
      ]
    }
    ```

## 3. Incident Management Endpoints
- `GET /api/incidents`
  - Query params: `page`, `size`, `status`, `severity`, `service`, `from`, `to`, `sort`
  - Response 200: Page of incident summaries
- `GET /api/incidents/{id}`
  - Response 200: Complete incident details with linked anomalies, impacted services, and active room ID
- `PATCH /api/incidents/{id}/status`
  - Body: `{ status: "INVESTIGATING|MITIGATING|RESOLVED|CLOSED", note: "..." }`
  - Response 200: Updated incident
- `POST /api/incidents/{id}/assign`
  - Body: `{ userId: "..." }`
  - Response 200: Assignment confirmed
- `GET /api/incidents/{id}/timeline`
  - Response 200: Chronological list of `incident_events`
- `POST /api/incidents/{id}/investigate`
  - Body: `{ force: false }`
  - Response 202: AI investigation task queued
- `GET /api/incidents/{id}/investigation`
  - Response 200: Structured AI report (`observations`, `hypotheses`, `recommendedInvestigation`, `similarIncidents`, `mode`)
- `GET /api/incidents/{id}/replay`
  - Response 200: Ordered stream of historical replay events
- `GET /api/incidents/{id}/what-changed`
  - Query params: `baselineMinutes=30`
  - Response 200: Metric deltas, new error logs, deployment diffs

## 4. War Room & Collaboration Endpoints
- `GET /api/incidents/{id}/messages`
  - Query params: `since_seq`, `limit`
  - Response 200: Monotonically ordered chat history
- `POST /api/incidents/{id}/messages`
  - Body: `{ clientMessageId, content, messageType: "TEXT|AI_NOTE|SYSTEM", attachments }`
  - Response 201: Persisted message with sequence number
- STOMP WebSocket endpoints:
  - Connect: `/ws/synapsex`
  - Subscribe: `/topic/incidents/{id}/war-room`
  - Send: `/app/incidents/{id}/send`
  - Presence: `/app/incidents/{id}/presence`

## 5. Knowledge & RAG Endpoints
- `GET /api/knowledge/search`
  - Query params: `query`, `service`, `cutoff=0.7`
  - Response 200: Matching historical incident post-mortems with similarity scores

## 6. Simulation Endpoints
- `POST /api/simulations/start`
  - Body: `{ scenario: "DATABASE_CONNECTION_POOL_EXHAUSTION", durationSeconds: 120, seed: 42 }`
  - Response 200: Simulation session metadata
- `POST /api/simulations/stop`
  - Response 200: Simulation stopped
- `GET /api/simulations/status`
  - Response 200: Active simulations, events generated, simulated incidents

## 7. Standardized Error Format (RFC 7807 ProblemDetail)
```json
{
  "type": "https://synapsex.io/errors/validation-failed",
  "title": "Bad Request",
  "status": 400,
  "detail": "Invalid telemetry payload",
  "instance": "/api/telemetry/events",
  "code": "SCHEMA_VALIDATION_ERROR",
  "traceId": "c8b417c8-89c0-48e0-bb1b-10f763f0e08b",
  "fieldErrors": [
    { "field": "metrics.latencyMs", "message": "must be greater than or equal to 0" }
  ]
}
```
