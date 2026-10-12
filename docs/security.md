# SynapseX Security Architecture & Threat Model

## 1. Authentication & Session Security
- **Password Security**: Passwords hashed with BCrypt (strength 12) with salt per entry. Plaintext passwords never logged.
- **JWT Architecture**:
  - Access Token: Short-lived HMAC-SHA256 (15 minutes expiry) carrying user ID, email, active workspace ID, and roles.
  - Refresh Token: Long-lived cryptographically secure random token (7 days expiry), hashed with SHA-256 before storage in `refresh_tokens`.
  - **Rotation & Reuse Detection**: Upon token refresh, the used refresh token is invalidated and a new pair is issued. If a previously invalidated refresh token is presented, the system triggers reuse detection, revoking all active refresh tokens for that user account and generating an audit security alert.

## 2. Authorization & Tenant Isolation
- **Role-Based Access Control (RBAC)**:
  - `ADMIN`: Full management of users, API keys, workspaces, and system configurations.
  - `INCIDENT_MANAGER`: Declare, assign, escalate, mitigate, and resolve incidents; trigger simulations.
  - `ENGINEER`: Participate in war rooms, view telemetry, inspect AI investigations, comment.
  - `VIEWER`: Read-only access to dashboard and incidents; war rooms subscription is view-only.
- **Workspace Multi-Tenant Boundary**:
  - Every tenant entity has a foreign key to `workspaces`.
  - Spring Security custom expression or method security (`@PreAuthorize("@workspaceSecurity.canAccess(authentication, #workspaceId)")`) enforces boundaries.
  - Repository queries explicitly include `workspace_id = :workspaceId`.
  - Cross-tenant access attempts immediately yield HTTP 403 Forbidden and log an audit event.

## 3. Telemetry Ingestion Security
- **API Key Scoping**: Ingestion requires header `X-API-Key: syn_live_...`. Raw keys are never stored; only SHA-256 hashes are persisted.
- **Token Bucket Rate Limiting**: In-memory token bucket per API key rejects traffic exceeding thresholds (default: 1000 req/sec burst 2000).
- **Payload Sanitization & Size Caps**: Max HTTP body size capped at 5MB for batch ingestion and 100KB for single events to defend against denial-of-service (DoS) memory exhaustion.

## 4. Prompt Injection Defense
- Untrusted user inputs, external logs, and telemetry fields passed to LLMs are wrapped in non-executable data delimiters (`<<<UNTRUSTED_DATA_START>>> ... <<<UNTRUSTED_DATA_END>>>`).
- Strip known instruction override sequences before prompt construction.
- Strict output validation against a defined JSON schema drops any uncited claims or unexpected tool invocations.

## 5. Audit Logging & Sensitive Actions
- All mutations (role modifications, incident escalations, manual overrides, simulation triggers) append an immutable record to `audit_logs` capturing `user_id`, `workspace_id`, `action`, `resource_id`, `ip_address`, and `timestamp`.
- Logback masking filters redact authorization headers, tokens, passwords, and sensitive keys from system logs.
