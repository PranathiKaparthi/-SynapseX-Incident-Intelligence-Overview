package com.synapsex.knowledge;

import com.synapsex.ai.provider.DemoProvider;
import com.synapsex.knowledge.dto.CreateKnowledgeDto;
import com.synapsex.knowledge.dto.KnowledgeMatchDto;
import com.synapsex.knowledge.model.IncidentEmbedding;
import com.synapsex.knowledge.model.IncidentKnowledge;
import com.synapsex.knowledge.repository.IncidentEmbeddingRepository;
import com.synapsex.knowledge.repository.IncidentKnowledgeRepository;
import com.synapsex.knowledge.service.RagService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RagServiceTest {

    @Mock private IncidentKnowledgeRepository knowledgeRepository;
    @Mock private IncidentEmbeddingRepository embeddingRepository;

    private RagService ragService;
    private DemoProvider demoProvider;

    private UUID workspaceA;
    private UUID workspaceB;

    @BeforeEach
    void setUp() {
        demoProvider = new DemoProvider();
        ragService = new RagService(knowledgeRepository, embeddingRepository, demoProvider);

        workspaceA = UUID.randomUUID();
        workspaceB = UUID.randomUUID();
    }

    @Test
    @DisplayName("Index resolved incident: generates embeddings and persists knowledge record")
    void testIndexResolvedIncident() {
        CreateKnowledgeDto dto = new CreateKnowledgeDto(
                UUID.randomUUID(),
                "HikariCP Connection Starvation",
                "Payment service ran out of connections under load",
                "Unindexed query in payment processing",
                "Added index on order_id",
                "[\"evt-101\"]"
        );

        when(knowledgeRepository.save(any(IncidentKnowledge.class))).thenAnswer(i -> i.getArgument(0));
        when(embeddingRepository.save(any(IncidentEmbedding.class))).thenAnswer(i -> i.getArgument(0));

        IncidentKnowledge indexed = ragService.indexResolvedIncident(workspaceA, dto);

        assertNotNull(indexed);
        assertEquals("HikariCP Connection Starvation", indexed.getTitle());
        verify(knowledgeRepository, times(1)).save(any(IncidentKnowledge.class));
        verify(embeddingRepository, times(1)).save(any(IncidentEmbedding.class));
    }

    @Test
    @DisplayName("RAG search: returns matching past incidents and filters out results below cutoff threshold")
    void testSearchSimilarWithCutoff() {
        UUID kId = UUID.randomUUID();
        IncidentKnowledge knowledge = new IncidentKnowledge(
                workspaceA, UUID.randomUUID(),
                "DB Pool Starvation", "High latency on payment-service", "Lock contention", "Increased pool", "[]");

        String matchText = "Database connection pool exhaustion on payment service";
        float[] vector = demoProvider.embed(matchText);

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(vector[i]);
        }

        IncidentEmbedding embedding = new IncidentEmbedding(kId, workspaceA, matchText, sb.toString());

        when(embeddingRepository.findByWorkspaceId(workspaceA)).thenReturn(List.of(embedding));
        when(knowledgeRepository.findById(kId)).thenReturn(Optional.of(knowledge));

        // High similarity search query
        List<KnowledgeMatchDto> highMatches = ragService.searchSimilar(
                workspaceA, "Database connection pool exhaustion on payment service", 0.70);

        assertFalse(highMatches.isEmpty(), "Should find match exceeding 0.70 cutoff");
        assertEquals("DB Pool Starvation", highMatches.get(0).title());
        assertTrue(highMatches.get(0).similarity() >= 0.70);

        // Very high cutoff (0.999) filters out non-identical matches
        List<KnowledgeMatchDto> strictMatches = ragService.searchSimilar(
                workspaceA, "Completely different query about DNS failure", 0.95);

        assertTrue(strictMatches.isEmpty(), "Matches below cutoff must be excluded");
    }

    @Test
    @DisplayName("Workspace Isolation: Workspace A embeddings are never returned when searching Workspace B")
    void testWorkspaceIsolationInRag() {
        // Workspace B has no indexed knowledge
        when(embeddingRepository.findByWorkspaceId(workspaceB)).thenReturn(List.of());

        List<KnowledgeMatchDto> results = ragService.searchSimilar(
                workspaceB, "Database connection pool exhaustion", 0.50);

        assertTrue(results.isEmpty(), "Workspace B must not receive any knowledge indexed in Workspace A");
        verify(embeddingRepository, times(1)).findByWorkspaceId(workspaceB);
        verify(embeddingRepository, never()).findByWorkspaceId(workspaceA);
    }
}
