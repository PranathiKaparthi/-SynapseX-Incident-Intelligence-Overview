package com.synapsex.ai.provider;

import com.synapsex.ai.dto.InvestigationResultDto;
import com.synapsex.incident.model.Incident;
import com.synapsex.ingestion.model.TelemetryEvent;

import java.util.List;

public interface LlmProvider {
    String getProviderName(); // "DEMO" | "OPENAI" | "ANTHROPIC" | "OLLAMA"
    InvestigationResultDto investigate(Incident incident, List<TelemetryEvent> evidenceEvents, String promptContext);
    String generateWarRoomAssistantMessage(String incidentTopic, String question, List<TelemetryEvent> recentEvents);
}
