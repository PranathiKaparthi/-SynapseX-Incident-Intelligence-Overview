package com.synapsex.common.config;

import com.synapsex.auth.model.SystemRole;
import com.synapsex.auth.model.User;
import com.synapsex.auth.model.Workspace;
import com.synapsex.auth.model.WorkspaceMember;
import com.synapsex.auth.repository.UserRepository;
import com.synapsex.auth.repository.WorkspaceMemberRepository;
import com.synapsex.auth.repository.WorkspaceRepository;
import com.synapsex.incident.model.MonitoredService;
import com.synapsex.incident.model.ServiceDependency;
import com.synapsex.incident.repository.ServiceDependencyRepository;
import com.synapsex.incident.repository.ServiceRepository;
import com.synapsex.ingestion.model.IngestionApiKey;
import com.synapsex.ingestion.repository.IngestionApiKeyRepository;
import com.synapsex.knowledge.dto.CreateKnowledgeDto;
import com.synapsex.knowledge.service.RagService;
import com.synapsex.processing.model.AnomalyRule;
import com.synapsex.processing.repository.AnomalyRuleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    public static final UUID DEFAULT_WORKSPACE_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final WorkspaceRepository workspaceRepository;
    private final UserRepository userRepository;
    private final WorkspaceMemberRepository memberRepository;
    private final ServiceRepository serviceRepository;
    private final ServiceDependencyRepository dependencyRepository;
    private final AnomalyRuleRepository ruleRepository;
    private final IngestionApiKeyRepository apiKeyRepository;
    private final RagService ragService;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(
            WorkspaceRepository workspaceRepository,
            UserRepository userRepository,
            WorkspaceMemberRepository memberRepository,
            ServiceRepository serviceRepository,
            ServiceDependencyRepository dependencyRepository,
            AnomalyRuleRepository ruleRepository,
            IngestionApiKeyRepository apiKeyRepository,
            RagService ragService,
            PasswordEncoder passwordEncoder) {
        this.workspaceRepository = workspaceRepository;
        this.userRepository = userRepository;
        this.memberRepository = memberRepository;
        this.serviceRepository = serviceRepository;
        this.dependencyRepository = dependencyRepository;
        this.ruleRepository = ruleRepository;
        this.apiKeyRepository = apiKeyRepository;
        this.ragService = ragService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        // If users already exist (e.g. seeded via Flyway in PostgreSQL), skip
        if (userRepository.count() > 0) {
            log.info("Database already seeded with users. Skipping DataInitializer.");
            return;
        }

        log.info("Database is empty. Seeding initial workspace, users, services, rules, and RAG knowledge...");

        // 1. Workspace
        Workspace ws = new Workspace(DEFAULT_WORKSPACE_ID, "Acme Corp Production", "acme-prod");
        ws = workspaceRepository.save(ws);

        // 2. Users (Password123!)
        String encodedPass = passwordEncoder.encode("Password123!");

        User alice = new User(null, "alice@acme.com", encodedPass, "Alice Engineer", SystemRole.ENGINEER);
        alice = userRepository.save(alice);
        memberRepository.save(new WorkspaceMember(ws, alice, SystemRole.ENGINEER));

        User admin = new User(null, "admin@acme.com", encodedPass, "Admin User", SystemRole.ADMIN);
        admin = userRepository.save(admin);
        memberRepository.save(new WorkspaceMember(ws, admin, SystemRole.ADMIN));

        User bob = new User(null, "bob@acme.com", encodedPass, "Bob Manager", SystemRole.INCIDENT_MANAGER);
        bob = userRepository.save(bob);
        memberRepository.save(new WorkspaceMember(ws, bob, SystemRole.INCIDENT_MANAGER));

        // 3. Monitored Services
        MonitoredService db = serviceRepository.save(new MonitoredService(null, ws.getId(), "postgres-db", "production", "TIER_1", "HEALTHY"));
        MonitoredService pay = serviceRepository.save(new MonitoredService(null, ws.getId(), "payment-service", "production", "TIER_1", "HEALTHY"));
        MonitoredService order = serviceRepository.save(new MonitoredService(null, ws.getId(), "order-service", "production", "TIER_1", "HEALTHY"));
        MonitoredService inv = serviceRepository.save(new MonitoredService(null, ws.getId(), "inventory-service", "production", "TIER_2", "HEALTHY"));
        MonitoredService notif = serviceRepository.save(new MonitoredService(null, ws.getId(), "notification-service", "production", "TIER_3", "HEALTHY"));
        MonitoredService gw = serviceRepository.save(new MonitoredService(null, ws.getId(), "api-gateway", "production", "TIER_1", "HEALTHY"));

        // 4. Dependencies
        dependencyRepository.save(new ServiceDependency(ws.getId(), pay.getId(), db.getId(), "CRITICAL"));
        dependencyRepository.save(new ServiceDependency(ws.getId(), order.getId(), pay.getId(), "CRITICAL"));
        dependencyRepository.save(new ServiceDependency(ws.getId(), order.getId(), inv.getId(), "HIGH"));
        dependencyRepository.save(new ServiceDependency(ws.getId(), gw.getId(), order.getId(), "CRITICAL"));
        dependencyRepository.save(new ServiceDependency(ws.getId(), gw.getId(), pay.getId(), "HIGH"));

        // 5. Default Anomaly Rules
        ruleRepository.save(new AnomalyRule(ws.getId(), "High Latency Rule", "latencyMs", "GREATER_THAN", 2000.0, 60, 300, "CRITICAL"));
        ruleRepository.save(new AnomalyRule(ws.getId(), "High Error Rate Rule", "errorRate", "GREATER_THAN", 10.0, 60, 300, "CRITICAL"));
        ruleRepository.save(new AnomalyRule(ws.getId(), "High CPU Rule", "cpuUsage", "GREATER_THAN", 85.0, 120, 300, "WARNING"));
        ruleRepository.save(new AnomalyRule(ws.getId(), "DB Connection Pool Starvation", "dbConnUtilization", "GREATER_THAN", 90.0, 60, 300, "CRITICAL"));
        ruleRepository.save(new AnomalyRule(ws.getId(), "Consumer Lag Rule", "consumerLag", "GREATER_THAN", 2000.0, 180, 300, "WARNING"));

        // 6. API Key
        apiKeyRepository.save(new IngestionApiKey(ws, "Default Ingestion Key", "syn-dev", "hash-syn-dev-key"));

        // 7. Seed Post-Mortem in pgvector / RAG memory
        try {
            ragService.indexResolvedIncident(
                    ws.getId(),
                    new CreateKnowledgeDto(
                            null,
                            "PostgreSQL Connection Pool Saturation in payment-service",
                            "HikariCP connection pool hit 100% capacity during flash sale. Downstream payment queries timed out after 30s.",
                            "Unindexed high-cardinality audit query held DB connections open for >25s under high concurrency, exhausting max-pool-size.",
                            "1. Kill stalled long-running audit query sessions on postgres-db.\n2. Scale HikariCP maximum-pool-size from 20 to 50.\n3. Add missing B-tree composite index on audit_records(workspace_id, created_at).\n4. Restart payment-service instances.",
                            "[\"evt-sim-1001\", \"evt-sim-1002\"]"
                    )
            );
        } catch (Exception e) {
            log.warn("Failed to seed initial RAG knowledge: {}", e.getMessage());
        }

        log.info("Database seeding complete! Seed users available: alice@acme.com, admin@acme.com, bob@acme.com (password: Password123!)");
    }
}
