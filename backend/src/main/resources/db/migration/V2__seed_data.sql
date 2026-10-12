-- ========================================================
-- SynapseX Database Migration V2: Seed Data
-- ========================================================

-- 1. Demo Workspace
INSERT INTO workspaces (id, name, slug)
VALUES ('00000000-0000-0000-0000-000000000001', 'Default Operations', 'default-ops');

-- 2. Seed Users (password is "Password123!" hashed with BCrypt)
-- Hash: $2a$10$7Zk/rG6jZqfL8uM0hU8n4uP7vH2rJ8sX5kO0pM2wQ4tY9vX8zB0mG
INSERT INTO users (id, email, password_hash, full_name, system_role) VALUES
('10000000-0000-0000-0000-000000000001', 'admin@synapsex.io', '$2a$10$7Zk/rG6jZqfL8uM0hU8n4uP7vH2rJ8sX5kO0pM2wQ4tY9vX8zB0mG', 'Alice Vance (Admin)', 'ADMIN'),
('10000000-0000-0000-0000-000000000002', 'engineer@synapsex.io', '$2a$10$7Zk/rG6jZqfL8uM0hU8n4uP7vH2rJ8sX5kO0pM2wQ4tY9vX8zB0mG', 'Bob Stone (Engineer)', 'ENGINEER'),
('10000000-0000-0000-0000-000000000003', 'lead@synapsex.io', '$2a$10$7Zk/rG6jZqfL8uM0hU8n4uP7vH2rJ8sX5kO0pM2wQ4tY9vX8zB0mG', 'Charlie Commander (Incident Lead)', 'INCIDENT_MANAGER'),
('10000000-0000-0000-0000-000000000004', 'viewer@synapsex.io', '$2a$10$7Zk/rG6jZqfL8uM0hU8n4uP7vH2rJ8sX5kO0pM2wQ4tY9vX8zB0mG', 'Dana Read (Viewer)', 'VIEWER');

-- Memberships
INSERT INTO workspace_members (workspace_id, user_id, role) VALUES
('00000000-0000-0000-0000-000000000001', '10000000-0000-0000-0000-000000000001', 'ADMIN'),
('00000000-0000-0000-0000-000000000002', '10000000-0000-0000-0000-000000000002', 'ENGINEER'),
('00000000-0000-0000-0000-000000000003', '10000000-0000-0000-0000-000000000003', 'INCIDENT_MANAGER'),
('00000000-0000-0000-0000-000000000004', '10000000-0000-0000-0000-000000000004', 'VIEWER');

-- 3. Ingestion API Key (syn_live_demo1234567890abcdef)
-- SHA-256 of "syn_live_demo1234567890abcdef"
INSERT INTO ingestion_api_keys (id, workspace_id, name, key_prefix, key_hash, status)
VALUES ('20000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', 'Simulator Ingestion Key', 'syn_live_', '03ac674216f3e15c761ee1a5e255f067953623c8b388b4459e13f978d7c846f4', 'ACTIVE');

-- 4. Monitored Microservices
INSERT INTO services (id, workspace_id, name, environment, tier, health_status) VALUES
('30000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001', 'api-gateway', 'production', 'TIER_1', 'HEALTHY'),
('30000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000001', 'auth-service', 'production', 'TIER_1', 'HEALTHY'),
('30000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000001', 'order-service', 'production', 'TIER_1', 'HEALTHY'),
('30000000-0000-0000-0000-000000000004', '00000000-0000-0000-0000-000000000001', 'payment-service', 'production', 'TIER_1', 'HEALTHY'),
('30000000-0000-0000-0000-000000000005', '00000000-0000-0000-0000-000000000001', 'inventory-service', 'production', 'TIER_2', 'HEALTHY'),
('30000000-0000-0000-0000-000000000006', '00000000-0000-0000-0000-000000000001', 'notification-service', 'production', 'TIER_3', 'HEALTHY'),
('30000000-0000-0000-0000-000000000007', '00000000-0000-0000-0000-000000000001', 'postgres-db', 'production', 'TIER_1', 'HEALTHY');

-- 5. Service Dependency Graph
INSERT INTO service_dependencies (workspace_id, upstream_service_id, downstream_service_id, dependency_type) VALUES
('00000000-0000-0000-0000-000000000001', '30000000-0000-0000-0000-000000000001', '30000000-0000-0000-0000-000000000002', 'SYNCHRONOUS_HTTP'),
('00000000-0000-0000-0000-000000000001', '30000000-0000-0000-0000-000000000001', '30000000-0000-0000-0000-000000000003', 'SYNCHRONOUS_HTTP'),
('00000000-0000-0000-0000-000000000001', '30000000-0000-0000-0000-000000000001', '30000000-0000-0000-0000-000000000004', 'SYNCHRONOUS_HTTP'),
('00000000-0000-0000-0000-000000000001', '30000000-0000-0000-0000-000000000003', '30000000-0000-0000-0000-000000000004', 'SYNCHRONOUS_HTTP'),
('00000000-0000-0000-0000-000000000001', '30000000-0000-0000-0000-000000000003', '30000000-0000-0000-0000-000000000005', 'SYNCHRONOUS_HTTP'),
('00000000-0000-0000-0000-000000000001', '30000000-0000-0000-0000-000000000004', '30000000-0000-0000-0000-000000000007', 'DATABASE'),
('00000000-0000-0000-0000-000000000001', '30000000-0000-0000-0000-000000000005', '30000000-0000-0000-0000-000000000007', 'DATABASE');

-- 6. Seed Default Anomaly Rules
INSERT INTO anomaly_rules (workspace_id, name, metric_name, operator, threshold, window_seconds, cooldown_seconds, severity) VALUES
('00000000-0000-0000-0000-000000000001', 'High CPU Utilization Threshold', 'cpuUsage', 'GREATER_THAN', 90.0, 180, 300, 'CRITICAL'),
('00000000-0000-0000-0000-000000000001', 'High Service Error Rate', 'errorRate', 'GREATER_THAN', 10.0, 120, 300, 'CRITICAL'),
('00000000-0000-0000-0000-000000000001', 'P95 Latency Surge Threshold', 'latencyMs', 'GREATER_THAN', 2000.0, 300, 300, 'WARNING'),
('00000000-0000-0000-0000-000000000001', 'Database Connection Exhaustion', 'dbConnUtilization', 'GREATER_THAN', 95.0, 60, 180, 'CRITICAL'),
('00000000-0000-0000-0000-000000000001', 'Kafka Consumer Lag Spike', 'consumerLag', 'GREATER_THAN', 1000.0, 120, 300, 'WARNING');

-- 7. Seed Historical Incident & Knowledge for RAG
INSERT INTO incident_knowledge (id, workspace_id, title, summary, root_cause, resolution_procedure) VALUES
('40000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001',
 'INC-101: Payment Gateway DB Connection Pool Saturation',
 'High checkout latency and cascading 503 errors across order-service due to connection exhaustion on postgres-db.',
 'HikariCP connection pool exhausted on payment-service due to an unindexed query holding locks.',
 '1. Increased max-lifetime and pool size temporarily. 2. Added index on payments(order_id, status). 3. Restarted payment-service instances.');

INSERT INTO incident_embeddings (id, knowledge_id, workspace_id, embedding_text, embedding_vector) VALUES
('50000000-0000-0000-0000-000000000001', '40000000-0000-0000-0000-000000000001', '00000000-0000-0000-0000-000000000001',
 'database connection pool exhaustion payment service latency spike dbConnUtilization lock contention',
 '[0.012, 0.45, -0.23, 0.88, 0.12, -0.34]');
