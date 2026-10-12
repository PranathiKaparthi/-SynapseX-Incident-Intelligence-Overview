package com.synapsex.replay.controller;

import com.synapsex.replay.dto.ReplayEventDto;
import com.synapsex.replay.dto.WhatChangedDto;
import com.synapsex.replay.service.TimeMachineService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/incidents/{incidentId}")
@Tag(name = "Time Machine & Replay", description = "Deterministic incident replay stream and What Changed analysis")
public class ReplayController {

    private final TimeMachineService timeMachineService;

    public ReplayController(TimeMachineService timeMachineService) {
        this.timeMachineService = timeMachineService;
    }

    @GetMapping("/replay")
    @Operation(summary = "Get deterministic replay event sequence for an incident")
    public ResponseEntity<List<ReplayEventDto>> getReplayStream(@PathVariable UUID incidentId) {
        List<ReplayEventDto> stream = timeMachineService.buildReplayStream(incidentId);
        return ResponseEntity.ok(stream);
    }

    @GetMapping("/what-changed")
    @Operation(summary = "Compute baseline vs incident window metric and dependency health deltas")
    public ResponseEntity<WhatChangedDto> getWhatChanged(
            @PathVariable UUID incidentId,
            @RequestParam(defaultValue = "30") int baselineMinutes) {
        WhatChangedDto diff = timeMachineService.computeWhatChanged(incidentId, baselineMinutes);
        return ResponseEntity.ok(diff);
    }
}
