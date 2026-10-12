# SynapseX Database Design & Schema Specification

## 1. Relational Topology & Tenancy Strategy
All tenant tables enforce multi-tenancy via a mandatory `workspace_id UUID NOT NULL` foreign key. Row-level tenancy is enforced at the repository query level and method security filters.

### 1.1 Core Entities & Index Justifications
- `users`: Authentication identities, password hashes (BCrypt), global system roles.
- `workspaces`: Tenant boundaries (`id`, `name`, `slug`, `created_at`).
- `workspace_members`: User memberships per workspace with specific scoped roles (`ADMIN`, `ENGINEER`, `INCIDENT_MANAGER`, `VIEWER`). Index: `(workspace_id, user_id)` UNIQUE.
- `services`: Monitored services (`id`, `workspace_id`, `name`, `environment`, `tier`, `health_status`). Index: `(workspace_id, name)` UNIQUE.
- `service_dependencies`: Directed dependency graph (`upstream_service_id`, `downstream_service_id`, `dependency_type`).
- `ingestion_api_keys`: Hashed API keys (`key_hash`, `workspace_id`, `name`, `status`, `expires_at`). Index: `(key_hash)` UNIQUE.
- `telemetry_events`: Normalized time-series telemetry.
  - Columns: `id`, `event_id`, `workspace_id`, `service_id`, `service_name`, `event_type`, `severity`, `event_time`, `ingested_time`, `metrics_json`, `logs_json`, `attributes_json`, `simulated`.
  - Indexes:
    - `(workspace_id, event_id)` UNIQUE (for deduplication at scale).
    - `(workspace_id, service_id, event_time DESC)` (for sliding window queries and time-series extraction).
    - `(workspace_id, event_time DESC)` (for global live feed and time-machine replay).
- `processed_events`: High-speed deduplication ledger for Kafka consumers `(workspace_id, event_id, processed_at)`.
- `anomaly_rules`: Configurable detection thresholds per workspace (`metric_name`, `operator`, `threshold`, `window_seconds`, `cooldown_seconds`, `severity`).
- `anomalies`: Detected anomalies with snapshot evidence.
  - Columns: `id`, `workspace_id`, `rule_id`, `service_id`, `metric_name`, `severity`, `status`, `start_time`, `end_time`, `evidence_json`.
  - Indexes: `(workspace_id, service_id, status)` (for fast active anomaly clustering).
- `incidents`: Correlated system incidents.
  - Columns: `id`, `workspace_id`, `title`, `summary`, `status`, `severity`, `correlation_key`, `root_cause_service_id`, `started_at`, `resolved_at`, `version` (Optimistic Locking).
  - Indexes: `(workspace_id, status, severity)` (for dashboard aggregation and filtering).
- `incident_events`: Immutable audit trail and state timeline (`incident_id`, `event_type`, `description`, `actor`, `created_at`).
- `incident_rooms` & `messages`:
  - `incident_rooms`: 1:1 room per incident.
  - `messages`: Monotonically sequenced chat (`id`, `room_id`, `seq_num`, `sender_id`, `sender_name`, `content`, `message_type`, `created_at`). Index: `(room_id, seq_num ASC)` UNIQUE.
- `ai_investigations`: Structured investigation reports (`incident_id`, `mode`, `status`, `observations_json`, `hypotheses_json`, `recommendations_json`, `insufficient_evidence`, `created_at`).
- `agent_executions`: Detailed audit log of parallel subagents (`investigation_id`, `agent_type`, `status`, `duration_ms`, `error_message`, `result_summary`).
- `incident_knowledge` & `incident_embeddings`:
  - `incident_knowledge`: Confirmed post-mortem records (`id`, `workspace_id`, `incident_id`, `summary`, `root_cause`, `resolution_procedure`).
  - `incident_embeddings`: pgvector embeddings with `vector(384)`.
  - Index: HNSW index on `embedding vector_cosine_ops` with workspace filtering.
- `outbox`: Transactional outbox records (`id`, `aggregate_type`, `aggregate_id`, `event_type`, `payload_json`, `status`, `created_at`).
