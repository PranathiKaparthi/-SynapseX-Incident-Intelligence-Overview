package com.synapsex.ingestion.controller;

import com.synapsex.auth.model.Workspace;
import com.synapsex.ingestion.dto.BatchIngestionResponseDto;
import com.synapsex.ingestion.dto.TelemetryEnvelopeDto;
import com.synapsex.ingestion.service.IngestionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/telemetry")
public class IngestionController {

    private final IngestionService ingestionService;

    public IngestionController(IngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    @PostMapping("/events")
    public ResponseEntity<Map<String, Object>> ingestSingle(
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @Valid @RequestBody TelemetryEnvelopeDto payload) {

        Workspace workspace = ingestionService.authenticateApiKey(apiKey);
        ingestionService.ingestSingle(payload, workspace);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                "eventId", payload.eventId(),
                "status", "ACCEPTED",
                "ingestedTime", Instant.now().toString()
        ));
    }

    @PostMapping("/batch")
    public ResponseEntity<BatchIngestionResponseDto> ingestBatch(
            @RequestHeader(value = "X-API-Key", required = false) String apiKey,
            @RequestBody List<TelemetryEnvelopeDto> batch) {

        Workspace workspace = ingestionService.authenticateApiKey(apiKey);
        BatchIngestionResponseDto response = ingestionService.ingestBatch(batch, workspace);

        // HTTP 207 Multi-Status
        return ResponseEntity.status(207).body(response);
    }
}
