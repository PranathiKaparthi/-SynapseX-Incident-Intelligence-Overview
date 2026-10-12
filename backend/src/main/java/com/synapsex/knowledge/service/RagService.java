package com.synapsex.knowledge.service;

import com.synapsex.ai.provider.EmbeddingProvider;
import com.synapsex.knowledge.dto.CreateKnowledgeDto;
import com.synapsex.knowledge.dto.KnowledgeMatchDto;
import com.synapsex.knowledge.model.IncidentEmbedding;
import com.synapsex.knowledge.model.IncidentKnowledge;
import com.synapsex.knowledge.repository.IncidentEmbeddingRepository;
import com.synapsex.knowledge.repository.IncidentKnowledgeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);

    private final IncidentKnowledgeRepository knowledgeRepository;
    private final IncidentEmbeddingRepository embeddingRepository;
    private final EmbeddingProvider embeddingProvider;

    public RagService(
            IncidentKnowledgeRepository knowledgeRepository,
            IncidentEmbeddingRepository embeddingRepository,
            EmbeddingProvider embeddingProvider) {
        this.knowledgeRepository = knowledgeRepository;
        this.embeddingRepository = embeddingRepository;
        this.embeddingProvider = embeddingProvider;
    }

    @Transactional
    public IncidentKnowledge indexResolvedIncident(UUID workspaceId, CreateKnowledgeDto dto) {
        IncidentKnowledge knowledge = new IncidentKnowledge(
                workspaceId,
                dto.incidentId(),
                dto.title(),
                dto.summary(),
                dto.rootCause(),
                dto.resolutionProcedure(),
                dto.evidenceRefsJson()
        );
        knowledge = knowledgeRepository.save(knowledge);

        String textToEmbed = String.format("%s %s %s %s",
                dto.title(), dto.summary(), dto.rootCause(), dto.resolutionProcedure());
        float[] vector = embeddingProvider.embed(textToEmbed);

        String vectorStr = serializeVector(vector);
        IncidentEmbedding embedding = new IncidentEmbedding(knowledge.getId(), workspaceId, textToEmbed, vectorStr);
        embeddingRepository.save(embedding);

        log.info("Indexed post-mortem knowledge {} with embedding vector for workspace {}", knowledge.getId(), workspaceId);
        return knowledge;
    }

    public List<KnowledgeMatchDto> searchSimilar(UUID workspaceId, String query, double cutoff) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        // Strict multi-tenant isolation: filter embeddings by workspaceId directly
        List<IncidentEmbedding> tenantEmbeddings = embeddingRepository.findByWorkspaceId(workspaceId);
        if (tenantEmbeddings.isEmpty()) {
            return List.of();
        }

        float[] queryVector = embeddingProvider.embed(query);
        List<ScoredEntry> scored = new ArrayList<>();

        for (IncidentEmbedding emb : tenantEmbeddings) {
            float[] storedVector = deserializeVector(emb.getEmbeddingVector());
            double sim = cosineSimilarity(queryVector, storedVector);
            if (sim >= cutoff) {
                scored.add(new ScoredEntry(emb.getKnowledgeId(), sim));
            }
        }

        scored.sort((a, b) -> Double.compare(b.score(), a.score()));

        List<KnowledgeMatchDto> matches = new ArrayList<>();
        for (ScoredEntry entry : scored) {
            knowledgeRepository.findById(entry.knowledgeId()).ifPresent(k -> {
                matches.add(new KnowledgeMatchDto(
                        k.getId(),
                        k.getIncidentId(),
                        k.getTitle(),
                        k.getSummary(),
                        k.getRootCause(),
                        k.getResolutionProcedure(),
                        Math.round(entry.score() * 100.0) / 100.0
                ));
            });
        }

        return matches;
    }

    private record ScoredEntry(UUID knowledgeId, double score) {}

    public static double cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length || a.length == 0) return 0.0;
        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA <= 0.0 || normB <= 0.0) return 0.0;
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private String serializeVector(float[] vector) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(vector[i]);
        }
        return sb.toString();
    }

    private float[] deserializeVector(String s) {
        if (s == null || s.isBlank()) return new float[0];
        String[] parts = s.split(",");
        float[] vector = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                vector[i] = Float.parseFloat(parts[i].trim());
            } catch (NumberFormatException ignored) {}
        }
        return vector;
    }
}
