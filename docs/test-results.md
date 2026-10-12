# SynapseX Automated Test Results Log

**Execution Date:** 2026-10-12  
**Platform:** Windows (JDK 21 LTS, Maven 3.9.9, Node v24.17.0, npm 11.13.0)  
**Strict Honesty Certification:** All test executions reported below were executed directly in the terminal environment with verifiable command outputs. Zero fabricated benchmarks or hypothetical test results.

---

## 1. Backend Automated Test Suite (JUnit 5 + Spring Boot)

Command executed:
```bash
mvn test
```

### Test Suite Summary:
```text
[INFO] -------------------------------------------------------
[INFO]  T E S T S
[INFO] -------------------------------------------------------
[INFO] Running com.synapsex.ai.AiInvestigationTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 2.832 s
[INFO] Running com.synapsex.auth.AuthServiceTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.074 s
[INFO] Running com.synapsex.collaboration.WarRoomServiceTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.667 s
[INFO] Running com.synapsex.incident.CorrelationAndLifecycleTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.854 s
[INFO] Running com.synapsex.ingestion.IngestionServiceTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 3.814 s
[INFO] Running com.synapsex.knowledge.RagServiceTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.341 s
[INFO] Running com.synapsex.notification.NotificationServiceTest
[INFO] Tests run: 3, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.171 s
[INFO] Running com.synapsex.replay.TimeMachineServiceTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.257 s
[INFO] 
[INFO] Results:
[INFO] 
[INFO] Tests run: 29, Failures: 0, Errors: 0, Skipped: 0
[INFO] 
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time:  18.583 s
```

### Test Cases Verified:
1. **`com.synapsex.auth.AuthServiceTest` (5 tests):**
   - `testRegisterSuccess`: User registration with BCrypt hashing and workspace binding.
   - `testLoginSuccess`: Authentication yielding access and refresh tokens.
   - `testRefreshTokenRotation`: Successful rotation issuing new paired tokens.
   - `testRefreshTokenReuseDetection`: Invalidation of all session tokens upon reuse of an already-rotated refresh token.
   - `testWorkspaceIsolationEnforcement`: Cross-workspace credential validation rejection.

2. **`com.synapsex.ingestion.IngestionServiceTest` (5 tests):**
   - `testApiKeyAuthentication`: Rejection of unauthorized ingestion requests.
   - `testBatchIngestion`: HTTP 207 Multi-Status per-event acceptance and rejection.
   - `testRateLimiterExceeded`: Bucket4j rate-limit enforcement and throttling.
   - `testConsumerIdempotency`: Idempotent deduplication based on `(workspace_id, event_id)`.
   - `testSimulatorDeterminism`: Seedable pseudo-random generator yielding identical event streams.

3. **`com.synapsex.incident.CorrelationAndLifecycleTest` (2 tests):**
   - `testCascadingVsIsolatedCorrelation`: Multi-hop BFS topology traversal correlates `postgres-db` -> `payment-service` -> `order-service` anomalies into one single incident while keeping disjoint `inventory-service` isolated.
   - `testStateMachineTransitions`: Full incident lifecycle transitions (`DETECTED` -> `INVESTIGATING` -> `IDENTIFIED` -> `MITIGATING` -> `RESOLVED` -> `CLOSED`) and incident reopening on recurring anomalies.

4. **`com.synapsex.collaboration.WarRoomServiceTest` (5 tests):**
   - `testMonotonicSequenceNumbering`: Strict server-assigned monotonic sequence numbers per room.
   - `testGapRecovery`: Clients catching up via `since_seq` recovering missing messages.
   - `testClientMessageDeduplication`: Client message deduplication via `clientMessageId`.
   - `testViewerRoleRejection`: RBAC write protection blocking `VIEWER` messages.
   - `testPresenceTracking`: Redis-compatible in-memory heartbeat tracking.

5. **`com.synapsex.ai.AiInvestigationTest` (4 tests):**
   - `testPromptInjectionDefense`: Neutralization of delimiter attacks and injection payloads.
   - `testEvidenceValidationDropsUncitedClaims`: Dropping uncited or non-existent telemetry citations.
   - `testInsufficientEvidenceFallback`: Safe fallback when evidence threshold is unsatisfied.
   - `testDemoProviderTransparency`: Prominent `"DEMO — not a real AI result"` disclosure and verified citation generation.

6. **`com.synapsex.knowledge.RagServiceTest` (3 tests):**
   - `testIndexResolvedIncident`: Indexing post-mortems with 384-dimensional dense vectors.
   - `testSearchSimilarWithCutoff`: Cosine similarity ranking and threshold filtering.
   - `testWorkspaceIsolationInRag`: Proof that workspace A cannot access workspace B's embeddings.

7. **`com.synapsex.notification.NotificationServiceTest` (3 tests):**
   - `testSendNotificationSuccess`: Persisting alert and publishing to WebSocket topic.
   - `testDuplicateNotificationSuppression`: Alert fatigue suppression for identical `dedupeKey`.
   - `testMarkAsReadCrossWorkspaceDefense`: Cross-workspace access defense.

8. **`com.synapsex.replay.TimeMachineServiceTest` (2 tests):**
   - `testDeterministicReplayStream`: Successive replay stream runs yield byte-identical sequences sorted strictly by `(timestamp ASC, sequenceId ASC)`.
   - `testWhatChangedComputation`: Baseline vs incident window percentage delta and dependency health computation.

---

## 2. Frontend Automated Test Suite (Vitest + React Testing Library)

Command executed:
```bash
npm test
```

### Test Suite Summary:
```text
 RUN  v2.1.9 C:/Users/kapar/OneDrive/Desktop/chatbox/frontend

 ✓ src/test/Dashboard.test.tsx (1 test) 277ms
 ✓ src/test/App.test.tsx (2 tests) 150ms

 Test Files  2 passed (2)
      Tests  3 passed (3)
   Duration  6.11s
```

### Production Build Verification:
Command executed:
```bash
npm run build
```

Output:
```text
> tsc && vite build

vite v5.4.21 building for production...
transforming...
✓ 2574 modules transformed.
rendering chunks...
computing gzip size...
dist/index.html                   0.56 kB │ gzip:   0.38 kB
dist/assets/index-ZrKrEsLK.css   29.99 kB │ gzip:   6.08 kB
dist/assets/index-hVTBI05o.js   749.07 kB │ gzip: 212.66 kB
✓ built in 13.79s
```
Result: TypeScript type checking passed (`tsc`), and production static bundle generated in `frontend/dist/`.
